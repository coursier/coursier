---
title: fetch
---

The `fetch` command, like the [`resolve` command](cli-resolve.md), resolves
the transitive dependencies of one or more dependencies. It also goes one step
further, by downloading their artifacts, and printing their paths.

Use like
```bash
$ coursier fetch org.http4s:http4s-dsl_2.12:0.18.21
/path/to/coursier/cache/v1/https/repo1.maven.org/maven2/org/scala-lang/scala-reflect/2.12.6/scala-reflect-2.12.6.jar
/path/to/coursier/cache/v1/https/repo1.maven.org/maven2/org/slf4j/slf4j-api/1.7.25/slf4j-api-1.7.25.jar
/path/to/coursier/cache/v1/https/repo1.maven.org/maven2/org/scala-lang/scala-library/2.12.7/scala-library-2.12.7.jar
/path/to/coursier/cache/v1/https/repo1.maven.org/maven2/org/http4s/http4s-websocket_2.12/0.2.1/http4s-websocket_2.12-0.2.1.jar
/path/to/coursier/cache/v1/https/repo1.maven.org/maven2/org/scodec/scodec-bits_2.12/1.1.6/scodec-bits_2.12-1.1.6.jar
/path/to/coursier/cache/v1/https/repo1.maven.org/maven2/org/typelevel/machinist_2.12/0.6.5/machinist_2.12-0.6.5.jar
/path/to/coursier/cache/v1/https/repo1.maven.org/maven2/org/typelevel/cats-effect_2.12/0.10.1/cats-effect_2.12-0.10.1.jar
/path/to/coursier/cache/v1/https/repo1.maven.org/maven2/org/typelevel/cats-core_2.12/1.4.0/cats-core_2.12-1.4.0.jar
/path/to/coursier/cache/v1/https/repo1.maven.org/maven2/org/typelevel/cats-macros_2.12/1.4.0/cats-macros_2.12-1.4.0.jar
/path/to/coursier/cache/v1/https/repo1.maven.org/maven2/org/log4s/log4s_2.12/1.6.1/log4s_2.12-1.6.1.jar
/path/to/coursier/cache/v1/https/repo1.maven.org/maven2/org/http4s/parboiled_2.12/1.0.0/parboiled_2.12-1.0.0.jar
/path/to/coursier/cache/v1/https/repo1.maven.org/maven2/org/typelevel/cats-kernel_2.12/1.4.0/cats-kernel_2.12-1.4.0.jar
/path/to/coursier/cache/v1/https/repo1.maven.org/maven2/org/http4s/http4s-core_2.12/0.18.21/http4s-core_2.12-0.18.21.jar
/path/to/coursier/cache/v1/https/repo1.maven.org/maven2/co/fs2/fs2-scodec_2.12/0.10.6/fs2-scodec_2.12-0.10.6.jar
/path/to/coursier/cache/v1/https/repo1.maven.org/maven2/org/http4s/http4s-dsl_2.12/0.18.21/http4s-dsl_2.12-0.18.21.jar
/path/to/coursier/cache/v1/https/repo1.maven.org/maven2/co/fs2/fs2-io_2.12/0.10.6/fs2-io_2.12-0.10.6.jar
/path/to/coursier/cache/v1/https/repo1.maven.org/maven2/co/fs2/fs2-core_2.12/0.10.6/fs2-core_2.12-0.10.6.jar
```

## Classpath format

Optionally, when passed the `-p` or `--classpath` option, the `fetch` command
can print its output in a format that can be passed as-is to `java -cp`, like
```bash
$ coursier fetch -p com.lihaoyi:ammonite_2.12.8:1.6.0
/path/to/coursier/cache/v1/https/repo1.maven.org/maven2/io/get-coursier/coursier_2.12/1.1.0-M7/coursier_2.12-1.1.0-M7.jar:/path/to/coursier/cache/v1/https/repo1.maven.org/maven2/org/jline/jline-terminal/3.6.2/jline-terminal-3.6.2.jar:…
$ java -cp "$(coursier fetch -p com.lihaoyi:ammonite_2.12.8:1.6.0)" ammonite.Main
Loading...
Welcome to the Ammonite Repl 1.6.0
(Scala 2.12.8 Java 1.8.0_121)
@
```

(Note that [Ammonite](https://ammonite.io) in the example above can be launched
directly and more conveniently with the [`launch` command](cli-launch.md).)

## Source JARs

To fetch source JARs rather than standard JARs, pass the `--sources` option,
like
```bash
$ coursier fetch --sources com.lihaoyi:ammonite_2.12.8:1.6.0
/path/to/coursier/cache/v1/https/repo1.maven.org/maven2/org/javassist/javassist/3.21.0-GA/javassist-3.21.0-GA-sources.jar
…
```

## Javadoc

To fetch javadoc JARs rather than standard JARs, pass the `--javadoc` option,
like
```bash
$ coursier fetch --javadoc com.lihaoyi:ammonite_2.12.8:1.6.0
/path/to/coursier/cache/v1/https/repo1.maven.org/maven2/com/lihaoyi/sourcecode_2.12/0.1.5/sourcecode_2.12-0.1.5-javadoc.jar
…
```

## Multiple classifiers

With both `--sources` and `--javadoc`, if you want to retain standard JARs along
sources or javadoc JARs, pass `--default=true` to force fetching the default
JARs too, like
```bash
$ coursier fetch --default=true --sources com.lihaoyi:ammonite_2.12.8:1.6.0
/path/to/coursier/cache/v1/https/repo1.maven.org/maven2/org/javassist/javassist/3.21.0-GA/javassist-3.21.0-GA-sources.jar
…
/path/to/coursier/cache/v1/https/repo1.maven.org/maven2/org/jline/jline-terminal/3.6.2/jline-terminal-3.6.2.jar
…
```
