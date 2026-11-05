# Building jssrepl

This guide explains how to compile jssrepl, on Linux, from source and package it.

- To install and run an already-built jssrepl, see **`installjssrepl.md`** or **`installjssrepl.pdf`**.


---

## 1. Requirements

- Install Java from graalvm(https://www.graalvm.org/), Go to https://github.com/graalvm/graalvm-ce-builds/releases/,
(GraalVM Community Version, we don't need PGO-Profile-Guided Optimization, which comes with the licensed graalVM version)
  last jss version I used : graalvm-community-25.4.4.1.1+1.1
- Install Maven(https://maven.apache.org/download.cgi), last jssrepl version, I used : Apache Maven 3.9.12
- Configure your path variable to run Java and Maven, example on Linux:

Add to your .bashrc, and restart your computer:

- export PATH="$HOME/Apps/Maven/apache-maven-3.9.12/bin:$PATH"
- export JAVA_HOME="$HOME/Apps/Java/graalvm-community-25.4.4.1.1+1.1"
- export PATH="$JAVA_HOME/bin:$PATH"

*** Restart your computer or load the new settings:
   ```bash
   source ~/.bashrc
   ```

- Get the jssrepl sources:

git clone https://github.com/LaranIkal/jssrepl.git

## 2. Create your build

Open the file manager and go to the directory where you have jssrepl, right-(mouse)clik on empty space and open a terminal window.

Happily compile with: mvn clean package

- Using file manager, go to directory jssrepl/target
- You will see jar: jssrepl.jar


Replace `jssrepl.jar` in directory jssrepl/aworkingjssrepl/jssrepl or if you already installed it on  `~/Apps/jssrepl`, replace the jar file in this directory. 
- To install a pre built jssrepl.jar, follow instructions in **`installjssrepl.md`**
