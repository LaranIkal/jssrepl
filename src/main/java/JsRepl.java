import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.PolyglotException;
import org.graalvm.polyglot.Source;
import org.graalvm.polyglot.Value;
import org.jline.keymap.KeyMap;
import org.jline.reader.Binding;
import org.jline.reader.EndOfFileException;
import org.jline.reader.LineReader;
import org.jline.reader.LineReaderBuilder;
import org.jline.reader.Reference;
import org.jline.reader.UserInterruptException;
import org.jline.reader.Widget;
import org.jline.terminal.Terminal;
import org.jline.terminal.TerminalBuilder;
import org.jline.utils.InfoCmp;

/**
 * jss - a small interactive JavaScript shell for GraalVM, in the spirit of the
 * R and Scala REPLs.
 *
 * Behavior, like `scala -i lib.scala` or sourcing a file in an R session:
 * any library files given on the command line are loaded into the session
 * FIRST, and then the shell stays open so you can keep working with what
 * they defined. Nothing here forces an exit unless you ask for one
 * (:quit, Ctrl-D).
 *
 * Line editing (via JLine):
 *   - Left/Right arrows move the cursor within the line being typed
 *     (this is JLine's normal default binding -- nothing custom needed).
 *   - Up/Down arrows browse a rolling window of the last 6 submitted
 *     commands, with this exact behavior:
 *       - Up shows the previous (older) command each press; from the
 *         oldest command, pressing Up again wraps around to the newest.
 *       - Down shows the next (newer) command each press; from the
 *         newest command, pressing Down again clears the line (blank).
 *       - Recalling a command always replaces whatever is currently
 *         typed on the line, with no extra blank lines.
 *       - If nothing has been entered yet, Up/Down do nothing.
 *
 * Usage:
 *   java -jar jss.jar                        start the REPL, nothing preloaded
 *   java -jar jss.jar lib1.js lib2.js         load lib1.js, lib2.js, THEN stay in the REPL
 *   java -jar jss.jar scriptsPath=/tmp/foo    stays in the REPL; not a file, so it's
 *                                             only available as parameters[1] / params.scriptsPath
 *   java -jar jss.jar -e "1 + 1"              evaluate one expression, print it, and exit
 *   java -jar jss.jar -h                      show usage
 *
 * `parameters` is always available inside JS as an array:
 *   - parameters[0] is always the jssrepl home directory (where jss.jar lives)
 *   - parameters[1], parameters[2], ... are whatever extra args you passed
 *   - any of those extra args shaped like "key=value" are ALSO parsed into
 *     a `params` object, e.g. js> params.scriptsPath   // "/tmp/foo"
 */
public class JsRepl {

    // A JS-side helper that turns any value into a readable string, entirely
    // inside the guest language so `typeof`/JSON semantics stay correct.
    private static final String FORMAT_FN_SRC =
            "(function __replFormat(v) {" +
            "  try {" +
            "    if (v === undefined) return 'undefined';" +
            "    if (v === null) return 'null';" +
            "    var t = typeof v;" +
            "    if (t === 'function') return v.toString();" +
            "    if (t === 'string') return JSON.stringify(v);" +
            "    if (t === 'object') {" +
            "      try { return JSON.stringify(v, null, 2); }" +
            "      catch (e) { return Object.prototype.toString.call(v) + ' ' + String(v); }" +
            "    }" +
            "    return String(v);" +
            "  } catch (e) {" +
            "    try { return String(v); } catch (e2) { return '<unprintable>'; }" +
            "  }" +
            "})";

    private static final int COMPLETE = 0;
    private static final int NEEDS_MORE = 1;
    private static final int SYNTAX_ERROR = -1;

    /** Max commands remembered for :history and Up/Down recall. */
    private static final int HISTORY_MAX = 6;

    private Context context;
    private Value formatFn;

    // Terminal/reader are only created for interactive mode (run()).
    private Terminal terminal;
    private LineReader lineReader;

    // Bounded command history, oldest at index 0, newest at the end.
    // Entries are always trimmed (see addToHistory) so recalling one never
    // injects a stray trailing newline into the edit buffer.
    private final List<String> history = new ArrayList<>();
    // -1 means "not currently recalling anything" (fresh/blank line).
    private int navIndex = -1;

    private final Set<String> initialGlobals = new LinkedHashSet<>();

    public JsRepl() {
        initContext();
    }

    private void initContext() {
        Context ctx;
        try {
            ctx = Context.newBuilder("js")
                    .allowAllAccess(true)
                    .option("js.ecmascript-version", "latest")
                    .build();
        } catch (IllegalArgumentException unsupportedOption) {
            // Older/newer GraalJS builds may not know this option name; fall
            // back to plain defaults rather than refusing to start.
            ctx = Context.newBuilder("js").allowAllAccess(true).build();
        }
        this.context = ctx;
        this.formatFn = context.eval("js", FORMAT_FN_SRC);

        initialGlobals.clear();
        for (String key : context.getBindings("js").getMemberKeys()) {
            initialGlobals.add(key);
        }
    }

    // ----------------------------------------------------------------
    // Entry point
    // ----------------------------------------------------------------

    public static void main(String[] args) throws IOException {
        if (args.length > 0 && (args[0].equals("-h") || args[0].equals("--help"))) {
            printUsage();
            return;
        }

        JsRepl repl = new JsRepl();

        if (args.length > 0 && (args[0].equals("-e") || args[0].equals("--eval"))) {
            if (args.length < 2) {
                System.out.println("Usage: jss -e \"<js code>\"");
                repl.context.close();
                return;
            }
            repl.evalAndPrint(args[1], "<eval>");
            repl.context.close();
            return;
        }

        // `parameters` is always populated, even with zero args: index 0 is
        // always the jssrepl home directory, and any args you passed follow
        // after it. Any "key=value" args are also parsed into `params`.
        //   java -jar jss.jar scriptsPath=/tmp/scripts
        //   js> const scriptsPath = parameters[1].split("=")[1]
        //   js> params.scriptsPath   // same thing, already parsed
        // Any arg that IS an existing file is ALSO loaded as a library
        // (scala -i / R source() style), then the REPL stays open.
        String home = resolveHomeDirectory();
        Value bindings = repl.context.getBindings("js");

        Value parametersArr = repl.context.eval("js", "[]");
        parametersArr.invokeMember("push", home);
        for (String a : args) {
            parametersArr.invokeMember("push", a);
        }
        bindings.putMember("parameters", parametersArr);
        bindings.putMember("params", repl.buildParamsObject(args));

        for (String a : args) {
            if (Files.exists(Paths.get(a))) {
                repl.loadFile(a);
            }
        }

        repl.run();
    }

    /** Directory the running jar (or classes dir) lives in. */
    private static String resolveHomeDirectory() {
        try {
            URI uri = JsRepl.class.getProtectionDomain().getCodeSource().getLocation().toURI();
            Path p = Paths.get(uri);
            if (Files.isRegularFile(p)) {
                p = p.getParent(); // directory containing jss.jar
            }
            return p.toAbsolutePath().normalize().toString();
        } catch (Exception e) {
            return System.getProperty("user.dir");
        }
    }

    private static void printUsage() {
        System.out.println("jss - JavaScript interactive shell using GraalVM");
        System.out.println();
        System.out.println("  jss                        start the REPL empty");
        System.out.println("  jss lib1.js lib2.js ...    load these files as libraries, then stay in the REPL");
        System.out.println("  jss key=value ...          extra args available as parameters[]/params.key");
        System.out.println("  jss -e \"<code>\"            evaluate one expression and exit");
        System.out.println("  jss -h                     show this help");
    }

    // ----------------------------------------------------------------
    // Interactive loop
    // ----------------------------------------------------------------

    public void run() throws IOException {
        terminal = TerminalBuilder.builder()
                .system(true)
                .build();
        lineReader = LineReaderBuilder.builder()
                .terminal(terminal)
                .build();
        installHistoryKeyBindings();

        printBanner();
        StringBuilder buffer = new StringBuilder();

        while (true) {
            String prompt = buffer.length() == 0 ? "js> " : "  ...> ";

            String line;
            try {
                line = lineReader.readLine(prompt);
            } catch (EndOfFileException eof) { // Ctrl-D
                break;
            } catch (UserInterruptException interrupt) { // Ctrl-C
                buffer.setLength(0); // abandon whatever was being typed/built
                System.out.println("^C");
                continue;
            }

            if (buffer.length() == 0) {
                String trimmed = line.trim();
                if (trimmed.startsWith(":")) {
                    if (handleCommand(trimmed)) {
                        break;
                    }
                    continue;
                }
                if (trimmed.isEmpty()) {
                    continue;
                }
            }

            buffer.append(line).append('\n');
            int status = analyze(buffer.toString());

            if (status == SYNTAX_ERROR) {
                System.out.println("SyntaxError: unexpected closing bracket");
                buffer.setLength(0);
                continue;
            }
            if (status == NEEDS_MORE) {
                continue; // keep reading more lines
            }

            String code = buffer.toString();
            buffer.setLength(0);
            addToHistory(code);
            evalAndPrint(code, "<repl:" + history.size() + ">");
        }

        System.out.println("\nBye.");
        context.close();
        try {
            if (terminal != null) {
                terminal.close();
            }
        } catch (IOException ignored) {
            // nothing useful to do on the way out
        }
    }

    private void printBanner() {
        System.out.println("jss - GraalVM JavaScript Polyglot Context interactive shell");
        System.out.println("Type JavaScript to evaluate it, or :help for commands.");
        System.out.println("Left/Right arrows edit the line; Up/Down browse the last "
                + HISTORY_MAX + " commands.");
        System.out.println("To see parameters when you ran jssrepl, type:parameters, it is a JavaScript array you can use.");
        System.out.println("jssrepl home directory is stored in parameters array.");
    }

    // ----------------------------------------------------------------
    // Up/Down history recall (custom widgets bound directly to the arrow
    // keys, bypassing JLine's own history mechanism so we get the exact
    // wrap-on-Up / blank-on-Down-overflow behavior described above).
    // Left/Right editing needs no setup: JLine's default key map already
    // binds those to cursor movement.
    // ----------------------------------------------------------------

    private void installHistoryKeyBindings() {
        Widget upWidget = this::onHistoryUp;
        Widget downWidget = this::onHistoryDown;

        lineReader.getWidgets().put("jss-history-up", upWidget);
        lineReader.getWidgets().put("jss-history-down", downWidget);

        KeyMap<Binding> mainMap = lineReader.getKeyMaps().get(LineReader.MAIN);
        mainMap.bind(new Reference("jss-history-up"), KeyMap.key(terminal, InfoCmp.Capability.key_up));
        mainMap.bind(new Reference("jss-history-down"), KeyMap.key(terminal, InfoCmp.Capability.key_down));
    }

    private boolean onHistoryUp() {
        if (history.isEmpty()) {
            return true; // nothing saved: do nothing
        }
        if (navIndex == -1) {
            navIndex = history.size() - 1; // first Up press: most recent command
        } else {
            navIndex--;
            if (navIndex < 0) {
                navIndex = history.size() - 1; // wrap from oldest back to newest
            }
        }
        replaceLine(history.get(navIndex));
        return true;
    }

    private boolean onHistoryDown() {
        if (history.isEmpty()) {
            return true; // nothing saved: do nothing
        }
        if (navIndex == -1) {
            return true; // not currently recalling anything: do nothing
        }
        navIndex++;
        if (navIndex >= history.size()) {
            navIndex = -1;
            replaceLine(""); // past the newest command: blank line
        } else {
            replaceLine(history.get(navIndex));
        }
        return true;
    }

    /**
     * Always overwrites whatever is currently typed on the line, with the
     * recalled command shown on a single line -- history entries are stored
     * pre-trimmed (see addToHistory) specifically so this never inserts a
     * stray trailing newline that would make JLine draw an extra blank
     * continuation line.
     */
    private void replaceLine(String text) {
        lineReader.getBuffer().clear();
        lineReader.getBuffer().write(text);
    }

    private void addToHistory(String code) {
        navIndex = -1; // any submission ends recall mode
        String trimmed = code.trim();
        if (trimmed.isEmpty()) {
            return;
        }
        history.add(trimmed);
        if (history.size() > HISTORY_MAX) {
            history.remove(0);
        }
    }

    // ----------------------------------------------------------------
    // Commands
    // ----------------------------------------------------------------

    /** Returns true if the REPL should exit. */
    private boolean handleCommand(String line) {
        String[] parts = line.split("\\s+", 2);
        String cmd = parts[0];
        String rest = parts.length > 1 ? parts[1].trim() : "";

        switch (cmd) {
            case ":quit":
            case ":exit":
            case ":q":
                return true;

            case ":help":
            case ":h":
            case ":?":
                printHelp();
                return false;

            case ":load":
            case ":l":
                if (rest.isEmpty()) {
                    System.out.println("Usage: :load <path-to-js-file> [more-files...]");
                } else {
                    for (String p : rest.split("\\s+")) {
                        loadFile(p);
                    }
                }
                return false;

            case ":reset":
                context.close();
                initContext();
                history.clear();
                navIndex = -1;
                System.out.println("Context reset.");
                return false;

            case ":vars":
                printVars();
                return false;

            case ":time":
                timeEval(rest);
                return false;

            case ":history":
                for (int i = 0; i < history.size(); i++) {
                    System.out.println("[" + (i + 1) + "] " + history.get(i));
                }
                return false;

            default:
                System.out.println("Unknown command: " + cmd + " (type :help)");
                return false;
        }
    }

    private void printHelp() {
        System.out.println("Commands:");
        System.out.println("  :help, :h          show this help");
        System.out.println("  :load <file...>, :l  load and run one or more .js files (\"libraries\") into this session");
        System.out.println("  :vars              list top-level bindings you've created this session");
        System.out.println("  :time <expr>       evaluate an expression and print how long it took");
        System.out.println("  :history           show the last " + HISTORY_MAX + " evaluated snippets");
        System.out.println("  :reset             discard all state and start a fresh JS context");
        System.out.println("  :quit, :exit, :q   exit the REPL (Ctrl-D also works)");
        System.out.println();
        System.out.println("Line editing: Left/Right move the cursor; Up/Down browse the last "
                + HISTORY_MAX + " commands (Up wraps from oldest to newest; Down clears the line past the newest).");
        System.out.println("Java interop is enabled: e.g. var List = Java.type('java.util.ArrayList')");
        System.out.println("parameters[0] is the jssrepl home directory; parameters[1..] are your extra args.");
        System.out.println("Multi-line input: unfinished brackets/strings automatically prompt '...>'");
    }

    private void loadFile(String path) {
        if (path == null || path.isEmpty()) {
            System.out.println("Usage: :load <path-to-jss-file>");
            return;
        }
        try {
            Path p = Paths.get(path);
            if (!Files.exists(p)) {
                System.out.println("File not found: " + path);
                return;
            } else if (Files.isDirectory(p)) {
                System.out.println("The parameter is a directory: " + path);
                System.out.println("To run a jss file do: :load <path-to-jss-file>");
                return;
            }

            Source src = Source.newBuilder("js", p.toFile()).build();
            Value result = context.eval(src);
            String formatted = formatFn.execute(result).asString();
            if ("undefined".equals(formatted)) {
                System.out.println("Loaded: " + path);
            } else {
                System.out.println("Loaded: " + path + "  => " + formatted);
            }
        } catch (IOException e) {
            System.out.println("Could not read file: " + e.getMessage());
        } catch (PolyglotException e) {
            reportGuestError(e);
        }
    }

    private void printVars() {
        Value bindings = context.getBindings("js");
        boolean any = false;
        for (String key : bindings.getMemberKeys()) {
            if (initialGlobals.contains(key)) {
                continue;
            }
            any = true;
            String formatted;
            try {
                formatted = formatFn.execute(bindings.getMember(key)).asString();
            } catch (Exception e) {
                formatted = "<unprintable>";
            }
            System.out.println(key + " = " + formatted);
        }
        if (!any) {
            System.out.println("(no user-defined top-level bindings yet)");
        }
    }

    private void timeEval(String code) {
        if (code.isEmpty()) {
            System.out.println("Usage: :time <expression>");
            return;
        }
        long start = System.nanoTime();
        evalAndPrint(code, "<time>");
        double ms = (System.nanoTime() - start) / 1_000_000.0;
        System.out.printf("(%.3f ms)%n", ms);
    }

    // ----------------------------------------------------------------
    // Evaluation helpers
    // ----------------------------------------------------------------

    private void evalAndPrint(String code, String name) {
        try {
            Source src = Source.newBuilder("js", code, name).buildLiteral();
            Value result = context.eval(src);
            String formatted = formatFn.execute(result).asString();
            // Statements like `print(...)`, `var x = 1`, or bare function
            // calls with no return value all evaluate to `undefined` in JS.
            // Printing that every time is just noise in a REPL, so skip it
            // (same idea as R/Scala suppressing a Unit result) -- if you
            // genuinely want to see it, wrap the expression: (undefined)
            if (!"undefined".equals(formatted)) {
                System.out.println(formatted);
            }
        } catch (PolyglotException e) {
            reportGuestError(e);
        }
    }

    private void reportGuestError(PolyglotException e) {
        if (e.isGuestException()) {
            System.out.println("Error: " + e.getMessage());
        } else {
            System.out.println("Internal error: " + e.getMessage());
        }
    }

    /**
     * Parses any "key=value" style args into a plain JS object, e.g.
     * ["scriptsPath=/tmp/foo", "justAFlag"] -> { scriptsPath: "/tmp/foo" }.
     * Args without an "=" are skipped (they're still available raw via the
     * `parameters` array).
     */
    private Value buildParamsObject(String[] args) {
        Value obj = context.eval("js", "({})");
        for (String a : args) {
            int eq = a.indexOf('=');
            if (eq <= 0) {
                continue; // no "key=" prefix; leave it out of params
            }
            String key = a.substring(0, eq);
            String value = a.substring(eq + 1);
            obj.putMember(key, value);
        }
        return obj;
    }

    // ----------------------------------------------------------------
    // Multi-line detection: is `code` a complete statement, or should the
    // REPL keep reading more lines? This is a lightweight bracket/string
    // tracker (not a full parser), which is enough for normal REPL use.
    // ----------------------------------------------------------------

    private int analyze(String code) {
        int depth = 0;
        char stringChar = 0;
        boolean inLineComment = false;
        boolean inBlockComment = false;
        boolean inTemplate = false;
        boolean escaped = false;

        for (int i = 0; i < code.length(); i++) {
            char c = code.charAt(i);
            char next = (i + 1 < code.length()) ? code.charAt(i + 1) : '\0';

            if (inLineComment) {
                if (c == '\n') inLineComment = false;
                continue;
            }
            if (inBlockComment) {
                if (c == '*' && next == '/') { inBlockComment = false; i++; }
                continue;
            }
            if (stringChar != 0) {
                if (escaped) { escaped = false; continue; }
                if (c == '\\') { escaped = true; continue; }
                if (c == stringChar) stringChar = 0;
                continue;
            }
            if (inTemplate) {
                if (escaped) { escaped = false; continue; }
                if (c == '\\') { escaped = true; continue; }
                if (c == '`') { inTemplate = false; }
                continue; // note: nested ${ } expressions aren't bracket-tracked
            }

            switch (c) {
                case '/':
                    if (next == '/') { inLineComment = true; i++; }
                    else if (next == '*') { inBlockComment = true; i++; }
                    break;
                case '\'':
                case '"':
                    stringChar = c;
                    break;
                case '`':
                    inTemplate = true;
                    break;
                case '(':
                case '[':
                case '{':
                    depth++;
                    break;
                case ')':
                case ']':
                case '}':
                    depth--;
                    break;
                default:
                    break;
            }

            if (depth < 0) {
                return SYNTAX_ERROR;
            }
        }

        if (depth > 0 || stringChar != 0 || inTemplate || inBlockComment) {
            return NEEDS_MORE;
        }
        return COMPLETE;
    }
}
