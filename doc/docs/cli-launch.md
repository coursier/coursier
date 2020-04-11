---
title: launch
---

`launch` launches applications from one or more dependencies.

```bash
$ cs launch org.scalameta::scalafmt-cli:2.4.2 -- --help
scalafmt 2.4.2
Usage: scalafmt [options] [<file>...]

  -h, --help               prints this usage text
  -v, --version            print version
…
```

## Arguments

Pass arguments to the launched program, by adding `--` after the coursier
arguments, like
```bash
$ coursier launch org.scalameta::scalafmt-cli:2.4.2 -- --version
```

## Main class

If the dependencies don't specify a default main class via
their manifest, or if the heuristics of the `launch` command fails to pick the
right manifest, you can specify a main class via
`-M` or `--main-class`, like
```bash
$ cs launch com.lihaoyi:ammonite_2.13.1:2.0.4 -M ammonite.Main
Loading...
Welcome to the Ammonite Repl 2.0.4 (Scala 2.13.1 Java 1.8.0_121)
@
```

If a dependency is published by sbt, and contains only one main
class, the main class should have been automatically added to the manifest.
If it contains several main classes, setting
[`mainClass`](https://github.com/sbt/sbt/blob/v1.2.8/main/src/main/scala/sbt/Keys.scala#L265)
in sbt is required for it to be set in the manifest.

In case several of the loaded JARs have manifests with main classes, the
`launch` command applies an heuristic to try to find the "main" one.

If the heuristic to find the main class fails, pass `-v -v` to `launch` to
get more details:
```bash
$ cs launch com.geirsson:scalafmt-cli_2.12:1.5.1 -r bintray:scalameta/maven -v -v -- --version
…
Found main classes:
  com.martiansoftware.nailgun.NGServer (vendor: , title: )
  org.scalafmt.cli.Cli (vendor: com.geirsson, title: cli)
…
```
This prints the main classes of all manifests found, among other things. If you think
one of them is the right one, just pass it via `-M` to `launch`.

## Java options

With the native coursier launcher, you can pass options to the JVM that
`launch` starts with `-J` or `--java-opt`, like
```bash
$ cs launch -J -Dfoo=bar -J -Xmx2g ammonite
Loading...
Welcome to the Ammonite Repl 2.0.4 (Scala 2.13.1 Java 1.8.0_121)
@ sys.props("foo")
res0: String = "bar"
```
