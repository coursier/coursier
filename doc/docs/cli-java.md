---
title: java
---

The `java` and `java-home` commands fetch and install JVMs.
The `java` command runs them too.

If a JVM is already installed on your system, the `java` command
simply calls it, just as if you called it yourself:
```bash
$ java -version
java version "1.8.0_121"
Java(TM) SE Runtime Environment (build 1.8.0_121-b13)
Java HotSpot(TM) 64-Bit Server VM (build 25.121-b13, mixed mode)
$ cs java -version
java version "1.8.0_121"
Java(TM) SE Runtime Environment (build 1.8.0_121-b13)
Java HotSpot(TM) 64-Bit Server VM (build 25.121-b13, mixed mode)
```

Likewise, the `java-home` command prints its base directory, which can look like
```bash
$ cs java-home
/Library/Java/JavaVirtualMachines/jdk1.8.0_121.jdk/Contents/Home
```

If no JVM is installed on your system, the default one is automatically
downloaded and
extracted in the [JVM cache](#managed-jvm-directory), and used. As of writing
this, the default JVM is the latest [AdoptOpenJDK](https://adoptopenjdk.net) 8 from the
[JVM index](#jvm-index).

## Managed JVMs

Both `java` and `java-home` commands accept a JVM name via `--jvm`. For example,
one can run the `java` binary from AdoptOpenJDK 13.0.2 via
```bash
$ cs java --jvm adopt:13.0-2 -version
openjdk version "13.0.2" 2020-01-14
OpenJDK Runtime Environment AdoptOpenJDK (build 13.0.2+8)
OpenJDK 64-Bit Server VM AdoptOpenJDK (build 13.0.2+8, mixed mode, sharing)
```

The AdoptOpenJDK 13.0.2 JVM is automatically downloaded and extracted
on your system if it's not already.

`cs java --available` lists the JVMs available in the index:
```bash
$ cs java --available | grep adopt
adopt:1.8.0-172
adopt:1.8.0-181
adopt:1.8.0-192
adopt:1.8.0-202
adopt:1.8.0-212
…
```

`cs java --installed` lists the ones currently installed on your system:
```bash
$ cs java --installed
adopt:1.8.0-242
adopt:1.11.0-6
adopt:1.13.0-2
graalvm:19.3.1
graalvm-java11:19.3.1
openjdk:1.14.0
```

## JVM index

Currently, the `java` and `java-home` commands rely on
[the index](https://github.com/shyiko/jabba/blob/master/index.json)
from the command-line tool [jabba](https://github.com/shyiko/jabba).
This index is regularly updated, and lists a large variety of JVMs
for Linux / macOS / Windows.

This index supports [Oracle OpenJDK](https://openjdk.java.net),
[AdoptOpenJDK](https://adoptopenjdk.net),
[GraalVM community edition](https://github.com/graalvm/graalvm-ce-builds),
[Zulu](https://www.azul.com/downloads/zulu-community),
among others.

To list the JVM that can be installed from it, run
```bash
$ cs java --available
```

If needed, that index could be complemented or replaced by
[an index of our own](https://github.com/coursier/jvm-index) at some point.

## Short JVM names

The full names from the index can be cumbersome to
remember and type (`adopt:13.0.2`, `adopt:1.8.0-232`, `graalvm:19.3.1`, etc.).

Shorter syntaxes are supported. If the version doesn't exactly match
an available one, it is assumed it corresponds to an interval. For example
`19.3` gets substituted by `19.3+`, which will match all 19.3.x versions,
and the latest one will be selected:
```bash
$ cs java-home --jvm graalvm:19.3 # picks graalvm:19.3.1
~/.cache/coursier/jvm/graalvm@19.3.1
```

```bash
$ cs java --jvm openjdk:14 -version # picks openjdk:1.14.0
openjdk version "14" 2020-03-17
OpenJDK Runtime Environment (build 14+35-1460)
OpenJDK 64-Bit Server VM (build 14+35-1460, mixed mode, sharing)
```

If one only specifies a version, with no JVM type upfront, AdoptOpenJDK
is used by default. This feature can be combined with version intervals above:
```bash
$ cs java --jvm 11 -version # picks adopt:11.0.6
openjdk version "11.0.6" 2020-01-14
OpenJDK Runtime Environment AdoptOpenJDK (build 11.0.6+10)
OpenJDK 64-Bit Server VM AdoptOpenJDK (build 11.0.6+10, mixed mode)
```

```bash
$ cs java --jvm 13 -version # picks adopt:13.0.2
openjdk version "13.0.2" 2020-01-14
OpenJDK Runtime Environment AdoptOpenJDK (build 13.0.2+8)
OpenJDK 64-Bit Server VM AdoptOpenJDK (build 13.0.2+8, mixed mode, sharing)
```

## Environment variables

Passing `--env` to the `java` command makes it print a bash script
that sets the right environment variables for a JVM to be used:
```bash
$ cs java --jvm 11 --env
export JAVA_HOME="/home/alex/.cache/coursier/jvm/adopt@1.11.0-6"
export PATH="/home/alex/.cache/coursier/jvm/adopt@1.11.0-6/bin:$PATH"
```

This script can be evaluated for the specified JVM to be used:
```bash
$ eval "$(cs java --jvm 11 --env)"
$ java -version
openjdk version "11.0.6" 2020-01-14
OpenJDK Runtime Environment AdoptOpenJDK (build 11.0.6+10)
OpenJDK 64-Bit Server VM AdoptOpenJDK (build 11.0.6+10, mixed mode)
```

Note that on macOS, `PATH` isn't updated, as the default `java` binary
reads `JAVA_HOME` and start `$JAVA_HOME/bin/java` itself.

Revert the changes made to the environment variables with
```bash
$ eval "$(cs java --disable)"
```

Note that you can call `--env` several times. Calling `--disable`
once will revert to the state right before the first call to `--env`:
```bash
$ eval "$(cs java --jvm 11 --env)"
$ eval "$(cs java --jvm 13 --env)"
$ eval "$(cs java --disable)" # reverts both commands above
```

## Managed JVM directory

The default directory for JVMs managed by coursier is OS-specific:
- Linux: `~/.cache/coursier/jvm`
- macOS: `~/Library/Caches/Coursier/jvm`
- Windows: `%LOCALAPPDATA%\Coursier\Cache\jvm`, typically looking like `C:\Users\Alex\AppData\Local\Coursier\Cache\jvm`

It can be changed via
- the `COURSIER_JVM_CACHE` environment variable, or
- the `coursier.jvm.cache` Java property.

Note that the `java`, `java-home`, and `setup` commands also accept a custom
managed JVM directory via `--jvm-dir`, like
```bash
$ cs java --jvm-dir my-custom-directory --jvm 11 -version
Extracting
  /Users/alex/Library/Caches/Coursier/v1/https/github.com/AdoptOpenJDK/openjdk11-binaries/releases/download/jdk-11.0.6%252B10/OpenJDK11U-jdk_x64_mac_hotspot_11.0.6_10.tar.gz
in
  my-custom-directory/adopt@1.11.0-6
Done
openjdk version "11.0.6" 2020-01-14
OpenJDK Runtime Environment AdoptOpenJDK (build 11.0.6+10)
OpenJDK 64-Bit Server VM AdoptOpenJDK (build 11.0.6+10, mixed mode)
```

## Parsing of arguments by the `java` command

The `java` command parses its arguments differently than the other coursier
commands.

It parses its arguments until it gets an option it doesn't recognize (see
`cs java --help` for the list of options it accepts). All arguments
from the first it doesn't recognize are passed as is to the `java` process it
starts. For example, in `cs java --jvm 11 -Xmx32m -version`, it doesn't recognize
`-Xmx32m`. So both `-Xmx32m` and `-version` are passed to `java` as is.

## System JVM detection

The `java` and `java-home` commands use the JVM setup on your system by default.

In order to find it, they successively:
- check the `JAVA_HOME` environment variable,
- on macOS, run `/usr/libexec/java_home`,
- on Linux and Windows, check for a `java` command in `PATH`.

If none of these methods finds a JVM, it is assumed no JVM was installed prior to running `cs java`
or `cs java-home`. Running one of these command will install the default JVM (as of writing this,
the latest AdoptOpenJDK 8 in the JVM index).
