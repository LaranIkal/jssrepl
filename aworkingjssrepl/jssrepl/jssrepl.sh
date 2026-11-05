# --- jss: GraalVM JDK Polyglot Context REPL launcher -------------------------------------------
# Add this to ~/.bashrc (or ~/.zshrc) and either `source ~/.bashrc` or open
# a new terminal. Then just type `jss` from anywhere.
#
# Edit this one line to point at wherever you built the project:

JSSREPL_HOME="$(cd "$(dirname "$0")" && pwd)"
JAR="$JSSREPL_HOME/jssrepl.jar"

if [ ! -f "$JAR" ]; then
  echo "jss: no jssrepl jar found at $JAR" >&2
  return 1
fi

java \
    --enable-native-access=ALL-UNNAMED \
    --sun-misc-unsafe-memory-access=allow \
    -Dpolyglot.engine.WarnInterpreterOnly=false \
    -cp $JSSREPL_HOME/jssrepl.jar:$JSSREPL_HOME/jarlib/* JsRepl $JSSREPL_HOME
#    -jar "$jar" "$@"

