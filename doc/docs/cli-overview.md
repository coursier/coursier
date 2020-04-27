---
title: Overview
---

The CLI of coursier has a number of commands to deal with dependencies and artifacts:
- `resolve` [lists the transitive dependencies](#resolve) of one or more dependencies,
- `fetch` [fetches the artifacts](#fetch) of one or more dependencies,
- `launch` [runs applications based on Maven / Ivy dependencies](#launch),
- `bootstrap` [generates convenient launchers to run them](#bootstrap),
- `install` [installs applications based on Maven / Ivy dependencies](#install),
- `java` and `java-home` [install, run, and get the home directory of JVMs](#java),
- `setup` [checks if your system has a JVM and the standard Scala applications](#setup), and installs them if needed.

See [Installation](cli-installation.md) for how to install the
CLI of coursier.

This page succinctly describes each of these commands. More
details about each them are then given in the dedicated
documentation page of each command (see links on the left).

## Available commands

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

### `launch`

`launch` launches applications from one or more dependencies.

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

`bootstrap` creates binary launchers from one or more dependencies.

```bash
$ cs bootstrap org.scalameta::scalafmt-cli:2.4.2 -o scalafmt
$ ./scalafmt --version
scalafmt 2.4.2
```

`bootstrap` can generate a [variety of launchers](cli-bootstrap.md#launcher-types),
including native ones like [GraalVM native images](cli-bootstrap.md#graalvm-native-image)
or [Scala Native executables](cli-bootstrap.md#scala-native).
See [the dedicated page](cli-bootstrap.md) for more details.

### `install`

The `install` command creates launchers for applications in the
[installation directory](https://get-coursier.io/docs/cli-install.html#installation-directory)
(`~/.local/share/coursier/bin` on Linux):
```bash
$ eval "$(cs install --env)" # add installation directory in PATH in the current session
$ cs install scalafmt
$ scalafmt --version
scalafmt 2.4.2
```

One can run `cs install --setup` to [update](https://get-coursier.io/docs/cli-setup.html#how-it-sets-environment-variables-globally) profile files (Linux / macOS) or user environment variables (Windows),
to add the installation directory to `PATH`.

See [the dedicated page](cli-install.md) for more details.

### `java`

The `java` command manages JVMs. For example, the following command
```bash
$ cs java --jvm 11 -version
openjdk version "11.0.6" 2020-01-14
OpenJDK Runtime Environment AdoptOpenJDK (build 11.0.6+10)
OpenJDK 64-Bit Server VM AdoptOpenJDK (build 11.0.6+10, mixed mode)
```
will automatically download the latest AdoptOpenJDK 11 (in the coursier cache), unpack it (in the [managed JVM directory](https://get-coursier.io/docs/cli-java.html#managed-jvm-directory)), and run it with `-version`.

It uses the [index](https://github.com/shyiko/jabba/blob/8c8e6be29610a3d5ea505087a791e9a57f6e48a6/index.json) of jabba to know where to download JVM archives, and assumes AdoptOpenJDK if only a version is passed.

The `java-home` command prints the Java home of a JVM, like
```bash
$ cs java-home --jvm 9
~/Library/Caches/Coursier/jvm/adopt@1.9.0-0/Contents/Home
```

One can [update](https://get-coursier.io/docs/cli-setup.html#how-it-sets-environment-variables-globally) profile files (Linux / macOS) or user environment variables (Windows) for a specific JVM with `--setup`, like
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

### `setup`

`setup` ensures that:
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

See the [dedicated page](cli-setup.md) for more details about what the `setup` command does.
