---
title: bootstrap
---

Like the [`fetch` command](cli-fetch.md), `bootstrap` resolves and fetches the
JARs of one or more dependencies. Like the [`launch` command](cli-launch.md),
it tries to find a [main class](cli-launch.md#main-class) in those JARs
(unless `-M` specifies one already). But unlike [`launch`](cli-launch.md), the
`bootstrap` command doesn't launch this main class straightaway. Instead, it
generates a (usually tiny) JAR, that can be renamed, copied, moved to another
machine or OS, and is able to download and launch the corresponding application.

For example,
```bash
$ coursier bootstrap com.geirsson:scalafmt-cli_2.12:1.5.1 -o scalafmt
$ ./scalafmt --version
scalafmt 1.5.1
```

## bootstrap content

The generated bootstraps are tiny Java apps, that upon launch, successively
- read a list of URLs for one of their resource files,
- read a main class name from another of their resource files,
- download the URLs missing from the coursier cache,
- get all the files corresponding to these URLs from the coursier cache,
- load those files in a class loader,
- find the main class by reflection,
- call the main method of the main class, which actually runs the application.

Overall, this "bootstraps" the application, hence the name.

All of that logic is handled by a small Java application, which currently
corresponds to the [`bootstrap-launcher` module](https://github.com/coursier/coursier/tree/bf9925778096eb24a3d3018079688d4255499457/modules/bootstrap-launcher)
of the coursier sources. A bootstrap consists of that module classes,
along with resource files (for the artifact URLs and main class to load) that
are specific to each application to launch.

## Java options

The generated bootstraps automatically pass their arguments starting with
`-J` to Java, stripping the leading `-J`, e.g.
```bash
$ coursier bootstrap com.lihaoyi:ammonite_2.12.8:1.6.0 -M ammonite.Main -o amm
$ ./amm -J-Dfoo=bar
Loading...
Welcome to the Ammonite Repl 1.6.0
(Scala 2.12.8 Java 1.8.0_121)
@ sys.props("foo")
res0: String = "bar"
```

Alternatively, one can hard-code Java options when generating the bootstrap,
by passing `--java-opt` options, like
```bash
$ coursier bootstrap com.lihaoyi:ammonite_2.12.8:1.6.0 -M ammonite.Main -o amm \
    --java-opt -Dfoo=bar
$ ./amm
Loading...
Welcome to the Ammonite Repl 1.6.0
(Scala 2.12.8 Java 1.8.0_121)
@ sys.props("foo")
res0: String = "bar"
```
