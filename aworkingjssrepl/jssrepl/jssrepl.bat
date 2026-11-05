@echo off
rem --- jss: GraalVM JDK Polyglot Context REPL launcher ---------------------------
rem Place this file in your jssrepl folder (next to jssrepl.jar and jarlib\) and
rem add that folder to your Path so you can type `jss` from anywhere.
rem Edit JSSREPL_HOME if you installed jssrepl somewhere else, or set the
rem JSSREPL_HOME environment variable before running this script.
setlocal

if "%JSSREPL_HOME%"=="" set "JSSREPL_HOME=%~dp0"

set "JAR=%JSSREPL_HOME%\jssrepl.jar"

if not exist "%JAR%" (
  echo jss: no jssrepl jar found at "%JAR%" 1>&2
  exit /b 1
)

java ^
  --enable-native-access=ALL-UNNAMED ^
  --sun-misc-unsafe-memory-access=allow ^
  -Dpolyglot.engine.WarnInterpreterOnly=false ^
  -cp "%JAR%;%JSSREPL_HOME%\jarlib\*" JsRepl %*

exit /b %ERRORLEVEL%
