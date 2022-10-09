---
title: Overview
---

The CLI of coursier is the Scala application and artifact manager.
It can install Scala applications and setup your Scala development environment.
It can also download and cache artifacts from the web.

See [Installation](cli-installation.md) for how to install the
CLI of coursier.

Once installed, the CLI of coursier provides a number of services:
- manage the installed Scala applications: [`install`, `list`, `update`, `uninstall`](#install), [`search`](#search)
- configure *channels* to install Scala applications from: `channel`
- launchers for Scala applications: [`launch`](#launch), [`bootstrap`](#bootstrap)
- manage the installed JVMs: [`java`, `java-home`](#java)
- directly manipulate Maven dependencies: [`fetch`](#fetch), [`resolve`](#resolve)
- perform [`setup`](#setup) again


This page succinctly describes each of these commands. More
details about each them are then given in the dedicated
documentation page of each command (see links on the left).

## Getting started

[Install `cs` and your Scala development environment](cli-installation.md)

## Main available commands

### `install`

The `install` command installs Scala applications in the
[installation directory](cli-install.md#installation-directory)
configured when installing `cs` (`~/.local/share/coursier/bin` by default on Linux):

```bash
$ cs install scalafmt
$ scalafmt --version
scalafmt 3.0.6
```

You may give a specific version number as follows:

```bash
$ cs install scalafmt:2.4.2
$ scalafmt --version
scalafmt 2.4.2
```

If the installation directory is not already on your `PATH`, you may add it for the current session with
```bash
$ eval "$(cs install --env)" # add installation directory in PATH in the current session
```

See [the dedicated page](cli-install.md) for more details about `install`.
It also presents the related commands `list`, `update` and `uninstall`.

### `search`

If you are not sure what the exact name of the application you want to install is, use the `search` command:

```bash
$ cs search fmt
scalafmt
```

### `launch`

`launch` launches applications from their name, or directly from one or more Maven dependencies.
The applications need not be installed with `install`.

```bash
$ cs launch scalafmt -- --help
scalafmt 3.0.6
Usage: scalafmt [options] [<file>...]

  -h, --help               prints this usage text
  -v, --version            print version
…
```

Like the `install` command, `launch` accepts an optional version number.
A typical use case is to start a Scala REPL with a specific Scala version:

```bash
$ cs launch scala:2.12.15
Welcome to Scala 2.12.15 (OpenJDK 64-Bit Server VM, Java 1.8.0_292).
Type in expressions for evaluation. Or try :help.

scala>
```

You may also combine `launch` with the [`fetch`](#fetch) command to run a Scala REPL with specific dependencies on the classpath:
```bash
$ cs launch scala:2.13.7 -- -cp $(cs fetch --scala 2.13.7 -p org.typelevel::cats-core:2.6.0)
```

In order to launch an application that does not have a published [application descriptor](cli-appdescriptors.md), `launch` accepts Maven coordinates as well:

```bash
$ cs launch org.scalameta::scalafmt-cli:2.4.2 -- --help
scalafmt 2.4.2
Usage: scalafmt [options] [<file>...]

  -h, --help               prints this usage text
  -v, --version            print version
…
```

Arguments can be passed to the application after `--`.

`launch` has more options, to [specify a main class](cli-launch.md#main-class),
[pass Java options](cli-launch.md#java-options), etc. See
[the dedicated page](cli-launch.md) for more details.

### `bootstrap`

`bootstrap` creates standalone binary launchers from one or more dependencies.
The produced launches do not need `cs` to be installed, so they can be shared, notably as part of a repository.

```bash
$ cs bootstrap org.scalameta::scalafmt-cli:2.4.2 -o scalafmt
$ ./scalafmt --version
scalafmt 2.4.2
```

On Linux, `bootstrap` does *not* create Windows `.bat` files by default.
If you use it to produce shareable scripts, it is recommended to add the `--bat` option in order for Windows users not be stuck:

```bash
$ cs bootstrap --bat org.scalameta::scalafmt-cli:2.4.2 -o scalafmt
$ ls
scalafmt scalafmt.bat
```

`bootstrap` can generate a [variety of launchers](cli-bootstrap.md#launcher-types),
including native ones like [GraalVM native images](cli-bootstrap.md#graalvm-native-image)
or [Scala Native executables](cli-bootstrap.md#scala-native).
See [the dedicated page](cli-bootstrap.md) for more details.

### `java`

The `java` command manages JVMs. For example, the following command
```bash
$ cs java --jvm 11 -version
openjdk version "11.0.6" 2020-01-14
OpenJDK Runtime Environment AdoptOpenJDK (build 11.0.6+10)
OpenJDK 64-Bit Server VM AdoptOpenJDK (build 11.0.6+10, mixed mode)
```
will automatically download the latest AdoptOpenJDK 11 (in the coursier cache), unpack it (in the [managed JVM directory](https://get-coursier.io/docs/cli-java.html#managed-jvm-directory)), and run it with `-version`.

It uses the [index](https://github.com/coursier/jvm-index) of to know where to download JVM archives, and assumes AdoptOpenJDK if only a version is passed.

The `java-home` command prints the Java home of a JVM, like
```bash
$ cs java-home --jvm 9
~/Library/Caches/Coursier/jvm/adopt@1.9.0-0/Contents/Home
```

One can [update](https://get-coursier.io/docs/cli-installation.html#how-it-sets-environment-variables-globally) profile files (Linux / macOS) or user environment variables (Windows) for a specific JVM with `--setup`, like
```bash
$ cs java --jvm openjdk:1.14 --setup
```
(This updates `JAVA_HOME` and `PATH`.)

You don't have to update these environment variables if you prefer not to.
For example, one can just get a Java home with
```bash
$ cs java-home --jvm graalvm:20
~/Library/Caches/Coursier/jvm/graalvm@20.0.0/Contents/Home
```
or set environment variables only for the current session with
```bash
$ eval "$(cs java --env --jvm 11)"
$ java -version
openjdk version "11.0.6" 2020-01-14
OpenJDK Runtime Environment AdoptOpenJDK (build 11.0.6+10)
OpenJDK 64-Bit Server VM AdoptOpenJDK (build 11.0.6+10, mixed mode)
```

These two commands only write things in the coursier cache, and the [managed JVM directory](https://get-coursier.io/docs/cli-java.html#managed-jvm-directory), which is a cache too (both of these directories live under `~/.cache/coursier` on Linux, `~/Library/Caches/Coursier` on macOS).

The [dedicated page](cli-java.md) gives more details about the `java` and `java-home` commands.

### `resolve`

`resolve` lists the transitive dependencies of
one or more other dependencies. Use like
```bash
$ cs resolve io.circe::circe-generic:0.12.3
com.chuusai:shapeless_2.13:2.3.3:default
io.circe:circe-core_2.13:0.12.3:default
io.circe:circe-generic_2.13:0.12.3:default
io.circe:circe-numbers_2.13:0.12.3:default
org.scala-lang:scala-library:2.13.0:default
org.typelevel:cats-core_2.13:2.0.0:default
org.typelevel:cats-kernel_2.13:2.0.0:default
org.typelevel:cats-macros_2.13:2.0.0:default
```

Note that this only relies on metadata files (POMs in particular),
and doesn't download any JAR.

`resolve` has more options, to [print trees](cli-resolve.md#tree),
[find which dependency brings another one](cli-resolve.md#what-depends-on),
etc. See [the dedicated page](cli-resolve.md) for more details.

### `fetch`

`fetch` fetches the JARs of one or more dependencies.

```bash
$ cs fetch io.circe::circe-generic:0.12.3
/path/to/coursier/cache/https/repo1.maven.org/maven2/io/circe/circe-generic_2.13/0.12.3/circe-generic_2.13-0.12.3.jar
/path/to/coursier/cache/https/repo1.maven.org/maven2/org/scala-lang/scala-library/2.13.0/scala-library-2.13.0.jar
/path/to/coursier/cache/https/repo1.maven.org/maven2/io/circe/circe-core_2.13/0.12.3/circe-core_2.13-0.12.3.jar
/path/to/coursier/cache/https/repo1.maven.org/maven2/com/chuusai/shapeless_2.13/2.3.3/shapeless_2.13-2.3.3.jar
/path/to/coursier/cache/https/repo1.maven.org/maven2/io/circe/circe-numbers_2.13/0.12.3/circe-numbers_2.13-0.12.3.jar
/path/to/coursier/cache/https/repo1.maven.org/maven2/org/typelevel/cats-core_2.13/2.0.0/cats-core_2.13-2.0.0.jar
/path/to/coursier/cache/https/repo1.maven.org/maven2/org/typelevel/cats-macros_2.13/2.0.0/cats-macros_2.13-2.0.0.jar
/path/to/coursier/cache/https/repo1.maven.org/maven2/org/typelevel/cats-kernel_2.13/2.0.0/cats-kernel_2.13-2.0.0.jar
```

`fetch` has more options, to [join its output with the path separator](cli-fetch.md#classpath-format),
fetch [source JARs](cli-fetch.md#source-jars), fetch [javadoc](cli-fetch.md#javadoc), etc. See
[the dedicated page](cli-fetch.md) for more details.

### `setup`

`setup` is the command used to install a Scala development environment.
It ensures that:
- a JVM is installed on your machine,
- the installation directory of the [`install`](#install) command is in your `PATH`,
- the standard Scala applications are installed on your system.

`setup` is a "repackaging" of features of the [`java`](#java) and [`install`](#install) commands.
One doesn't have to run `setup` for the other commands to work fine, it's just a convenience.

The features that `setup` wraps up are not tied to each other. You can rely
on the `java` or `java-home` commands while managing applications with your preferred package
manager. Or you can manage JVMs any other way while using the `install` command.
`setup` only conveniently sets them up in one go, and it only sets up a JVM if it doesn't find
one already installed.

See the [dedicated page](cli-installation.md) for more details about what the `setup` command does.
