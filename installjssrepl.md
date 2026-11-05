# jssrepl Install and first run 

This guide tells you how to have a working `jssrepl` on Linux and Windows:

---

## 1. Requirements

| Item | Requirement |
|---|---|
| JDK | GraalVM Community JDK 25(https://www.graalvm.org/), minimum version **graalvm-community-25.4.4.1.1+1.1** |
| Files | Files and folders in directory `jssrepl/aworkingjssrepl` |
| Disk space | About 500 MB for the JDK, plus a few MB for jssrepl |

> **Note.** jssrepl needs GraalVM JDK 25(graalvm-community-25.4.4.1.1+1.1) or newer, so older JDKs will not work.
To run jssrepl, you also may use other JDK than GraalVM, like: https://adoptium.net/ JDK 25

---

## 2. Install GraalVM JDK 25

Download GraalVM Community from the official pages:

- Download page: <https://www.graalvm.org/downloads/>
- All releases: <https://github.com/graalvm/graalvm-ce-builds/releases>

Choose the build for your operating system and CPU architecture, version **25.4.4.1.1+1.1 or newer**. The exact file names depend on the release, so check the asset list on the release page.

### Linux

1. Extract the JDK archive into a folder of your choice, for example `~/Apps`:

   ```bash
   mkdir -p ~/Apps
   tar -xzf graalvm-community-jdk-*_linux-x64_bin.tar.gz -C ~/Apps
   ```

   Use the file you downloaded. The name above is an example, and ARM machines use an `aarch64` build( We don't have ARM, your testing is welcome ).

2. Find the extracted folder name and set `JAVA_HOME`. Add these lines to `~/.bashrc` (or `~/.zshrc`), replacing the folder name with the one you actually got, example:

   ```bash
   export JAVA_HOME="$HOME/Apps/graalvm-community-openjdk-25.0.0+0.0"
   export PATH="$JAVA_HOME/bin:$PATH"
   ```

3. Load the new settings:

   ```bash
   source ~/.bashrc
   ```

### Windows

1. Extract the downloaded JDK `.zip` file, for example into `C:\Apps`. You should end up with a folder like `C:\Apps\graalvm-community-openjdk-25.x.x`.

2. Open **Start**, search for **Environment Variables**, and choose **Edit environment variables for your account**.

3. Create a new user variable:(If you are not able to do this, see the note below)

   | Name | Value |
   |---|---|
   | `JAVA_HOME` | `C:\Apps\graalvm-community-openjdk-25.x.x` (your actual folder) |

4. Edit the user variable `Path` and add a new entry: `%JAVA_HOME%\bin`

5. Click OK on all dialogs, then **open a new terminal** so the changes take effect.

### Verify the JDK

Run this in a new terminal:

```text
java -version
```

The output must mention **GraalVM** and version **25**. If it shows another Java version, your `PATH` is picking up a different JDK. Make sure `$JAVA_HOME/bin` (Linux) or `%JAVA_HOME%\bin` (Windows) comes first in `PATH`.

> **Note.** Windows - We have seen some companies block editing the environment variables, if so, after next step, rename folder from graalvm-community-25.4.4.1.1+1.1 to graalvmjdk25, keeping path names simpler, so you will have:

C:\Apps\graalvmjdk25

Edit jssrepl.bat and set line:
- java ^ 
  - to 
- C:\Apps\graalvmjdk25\bin\java ^
---

## 3. Install jssrepl.jar and its libraries

jssrepl layout:

```text
jssrepl/
  jarlib/            Library jars, such as JDBC Java libraries.
  scripts/           Your scripts, to keep them organized in a folder :)
  jssrepl.jar        The compiled REPL
  jssrepl.sh         Linux launcher (section 4)
  jssrepl.bat        Windows launcher (section 4)
```

Extract the REPL engine and scripts(jssrepl folder), from folder jssrepl/aworkingjssrepl to folder $HOME/Apps on Linux or C:\Apps on Windows

You must have folder: $HOME/Apps/jssrepl or C:\Apps\jss\jssrepl


## 4. Start the jssrepl

### Linux, Open a terminal window in $HOME/Apps/jssrepl, and run: `./jssrepl.sh`

### Windows, Open a terminal window in C:\Apps\jssrepl, and run: `jssrepl.bat`

The launcher starts Java with the right options and classpath.

| Option | Purpose |
|---|---|
| `--enable-native-access=ALL-UNNAMED` | Allows JLine and GraalVM to use native access without warnings |
| `--sun-misc-unsafe-memory-access=allow` | Allows libraries that still use `sun.misc.Unsafe` memory access on newer JDKs |
| `-Dpolyglot.engine.WarnInterpreterOnly=false` | Hides the "interpreter only" performance warning |
| `-cp jssrepl.jar:jarlib/*` | Puts the shell and all libraries on the classpath |
| `JsRepl` | The main class |




**5. Work with the interactive shell**

After starting the REPL, you should see the banner and the `js>` prompt:

```text
/.../jssrepl$ ./jssrepl.sh 
The parameter is a directory, maybe jssrepl home?: /home/you/Apps/jssrepl
To run a jss file do: load <path-to-jss-file>
jss - GraalVM JavaScript Polyglot Context interactive shell
Type JavaScript to evaluate it, or :help for commands.
Left/Right arrows edit the line; Up/Down browse the last 6 commands.
To see parameters when you ran jssrepl, type:parameters, it is a JavaScript array you can use.
jssrepl home directory is stored in parameters array.
js> 

...
js>
js> parameters
[
  "/home/you/Apps/jssrepl"
]
js> 

```

Try a few lines:

```text
js> 6 * 7
42
js> "hello".toUpperCase()
"HELLO"
js> parameters[0]
"/home/you/Apps/jssrepl"
js> :quit
```

`parameters[0]` shows the folder that contains `jssrepl.jar`, which confirms the launcher is using the right jar.

**4. Loading a library file from your jssrepl**

Using a text editor, create file `hello.jss` save it to your jssrepl/scripts folder, load it and call the function - hello("YourName"):

```javascript
function hello(name) { return "Hello, " + name + "!" }
```

Start your jssrepl and load your `hello.jss`:

```text
js> load(parameters[0] + '/scripts/hello.jss')
...
js> hello("YourName")
"Hello, YourName!"
```
There are some scripts you can use, load the script and call the function:
```text
js> load(parameters[0] + '/scripts/helloworld.jss')
Call The Function, type:helloworld()
...
js> helloworld()
"Hello World!!!"
js> 

```
```text
js> load(parameters[0] + '/scripts/csv_to_json.jss')
Call The Function, type:CSV_to_JSON()
...
js> CSV_to_JSON()
Error: {"error":"Cannot read CSV file","message":"java.io.FileNotFoundException:
 /home/you/Apps/jssrepl/data/sample.csv (No such file or directory)"}
js>
```
> *** When errors like this happen, just fix it in your files and reload the script, this case we fixed, the config.jss, from `data` to `scripts/data`, and reload the script.
```text
js> load(parameters[0] + '/scripts/csv_to_json.jss')
Call The Function, type:CSV_to_JSON()
...
js> CSV_to_JSON()
{
  "data": [
    {
      "name": "Alice",
      "age": "30",
      "city": "London"
    },
    {
      "name": "Bob",
      "age": "25",
      "city": "Paris"
    },
    {
      "name": "Charlie",
      "age": "22",
      "city": "Berlin"
    }
  ]
}
js> 
```
Script to read a csv using JDBC:

```text
js> load(parameters[0] + '/scripts/csvreader_jdbc.jss')
Connected successfully to: scripts/data
Name|Age|City
Alice|30|New York
Bob|25|London
Charlie|35|Tokyo
```

Script to get the weather:

```text
js> load(parameters[0] + '/scripts/getweather.jss')
Response Status: 200
{
  "location": {
    "name": "Moor Side",
    "region": "Lincolnshire",
    "country": "United Kingdom",
    "lat": 53.1,
    "lon": -0.142,
    "tz_id": "Europe/London",
    "localtime_epoch": 1793831494,
    "localtime": "2026-11-04 22:31"
  },
  "current": {
    "last_updated_epoch": 1793831400,
    "last_updated": "2026-11-04 22:30",
    "temp_c": 10.4,
    "temp_f": 50.7,
    "is_day": 0,
    "condition": {
      "text": "Overcast",
      "icon": "//cdn.weatherapi.com/weather/64x64/night/122.png",
      "code": 1009
    },
    "wind_mph": 14.5,
    "wind_kph": 23.4,
    "wind_degree": 85,
    "wind_dir": "E",
    "pressure_mb": 1031,
    "pressure_in": 30.45,
    "precip_mm": 0,
    "precip_in": 0,
    "humidity": 66,
    "cloud": 88,
    "feelslike_c": 6.7,
    "feelslike_f": 44.1,
    "windchill_c": 7.6,
    "windchill_f": 45.6,
    "heatindex_c": 10.4,
    "heatindex_f": 50.7,
    "dewpoint_c": 4.3,
    "dewpoint_f": 39.7,
    "vis_km": 10,
    "vis_miles": 6,
    "uv": 0,
    "gust_mph": 23.5,
    "gust_kph": 37.8,
    "will_it_rain": 0,
    "chance_of_rain": 15,
    "will_it_snow": 0,
    "chance_of_snow": 0,
    "wetbulb_c": 7.4,
    "wetbulb_f": 45.3
  }
}
js>
```

---

## 6. Troubleshooting

| Problem | Cause and fix |
|---|---|
| `java: command not found` or `'java' is not recognized` | `JAVA_HOME` or `PATH` is not set, or you did not open a new terminal. Redo section 2 |
| `java -version` does not mention GraalVM or shows a version below 25 | Another JDK is first in `PATH`. Move `$JAVA_HOME/bin` (Linux) or `%JAVA_HOME%\bin` (Windows) to the front |
| `UnsupportedClassVersionError` | The JDK running jssrepl is older than the one that built it. Use GraalVM JDK 25 or newer |
| `jss: no jssrepl jar found at ...` | `jssrepl.jar` is not where `JSSREPL_HOME` points. Copy it there (section 3) or fix `JSSREPL_HOME` in the launcher |
| `Error: Could not find or load main class JsRepl` | The jar does not contain the class. Run `jar tf jssrepl.jar` and look for `JsRepl.class`. If missing, rebuild using `Buildingjssrepl.md` |
| `NoClassDefFoundError: org/graalvm/polyglot/...` or `org/jline/...` | The `jarlib` folder is missing or incomplete. Copy the full folder next to `jssrepl.jar` |
| `No language and polyglot implementation was found` | The GraalJS jars are missing from `jarlib`. Rebuild the library folder using `Buildingjssrepl.md`, section 3 |
| `WARNING: A restricted method ... has been called` | The `--enable-native-access=ALL-UNNAMED` option is missing from your launcher. Use the scripts in section 4 exactly |
| Arrow keys print `^[[A` instead of recalling history | The terminal is not detected as a real terminal. Run jss from a normal terminal window, not from an IDE console or a pipe. On Windows, use Windows Terminal or PowerShell |
| `Permission denied` when running `jssrepl.sh` | Run `chmod +x ~/Apps/jssrepl/jssrepl.sh` |
| `jssrepl: command not found` (Linux) | `~/.local/bin` is not in `PATH`, or you did not run `source ~/.bashrc` |
| `'jssrepl' is not recognized` (Windows) | The jssrepl folder is not in `Path`, or the terminal was opened before you changed it. Open a new terminal |
