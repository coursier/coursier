---
title: fetch
---

`fetch` fetches the JARs of one or more dependencies. Use it like
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

Note that JARs are printed in a deterministic order, with
the JAR of a dependency before those of its dependencies. Pipe
the output of `fetch` to `sort` to make it more readable,
or use `resolve` if you are only looking for a dependency list.

## Classpath format

Pass `--classpath` to `fetch` to separate the elements it prints
with the OS-dependent path separator, `:` on Linux and macOS, and `;`
on Windows:
```bash
$ cs fetch --classpath io.circe::circe-generic:0.12.3
/path/to/coursier/cache/https/repo1.maven.org/maven2/io/circe/circe-generic_2.13/0.12.3/circe-generic_2.13-0.12.3.jar:/path/to/coursier/cache/https/repo1.maven.org/maven2/org/scala-lang/scala-library/2.13.0/scala-library-2.13.0.jar:/path/to/coursier/cache/https/repo1.maven.org/maven2/io/circe/circe-core_2.13/0.12.3/circe-core_2.13-0.12.3.jar:/path/to/coursier/cache/https/repo1.maven.org/maven2/com/chuusai/shapeless_2.13/2.3.3/shapeless_2.13-2.3.3.jar:/path/to/coursier/cache/https/repo1.maven.org/maven2/io/circe/circe-numbers_2.13/0.12.3/circe-numbers_2.13-0.12.3.jar:/path/to/coursier/cache/https/repo1.maven.org/maven2/org/typelevel/cats-core_2.13/2.0.0/cats-core_2.13-2.0.0.jar:/path/to/coursier/cache/https/repo1.maven.org/maven2/org/typelevel/cats-macros_2.13/2.0.0/cats-macros_2.13-2.0.0.jar:/path/to/coursier/cache/https/repo1.maven.org/maven2/org/typelevel/cats-kernel_2.13/2.0.0/cats-kernel_2.13-2.0.0.jar
```

When passed `--classpath`, the output of `fetch` can be passed as is
to `java -cp` for example,
```bash
$ CP="$(cs fetch --classpath org.scalameta::scalafmt-cli:latest.release)"
$ java -cp "$CP" org.scalafmt.cli.Cli --help
```
Note that there are easier ways to start applications, see
[the `launch` command](cli-launch.md).

## Source JARs

To fetch source JARs rather than standard JARs, pass the `--sources` option,
like
```bash
$ cs fetch --sources com.lihaoyi:ammonite_2.12.8:1.6.0
/path/to/coursier/cache/v1/https/repo1.maven.org/maven2/org/javassist/javassist/3.21.0-GA/javassist-3.21.0-GA-sources.jar
…
```

## Javadoc

To fetch javadoc JARs rather than standard JARs, pass the `--javadoc` option,
like
```bash
$ cs fetch --javadoc com.lihaoyi:ammonite_2.12.8:1.6.0
/path/to/coursier/cache/v1/https/repo1.maven.org/maven2/com/lihaoyi/sourcecode_2.12/0.1.5/sourcecode_2.12-0.1.5-javadoc.jar
…
```

## Multiple classifiers

With both `--sources` and `--javadoc`, if you want to retain standard JARs along
sources or javadoc JARs, pass `--default=true` to force fetching the default
JARs too, like
```bash
$ cs fetch --default=true --sources com.lihaoyi:ammonite_2.12.8:1.6.0
/path/to/coursier/cache/v1/https/repo1.maven.org/maven2/org/javassist/javassist/3.21.0-GA/javassist-3.21.0-GA-sources.jar
…
/path/to/coursier/cache/v1/https/repo1.maven.org/maven2/org/jline/jline-terminal/3.6.2/jline-terminal-3.6.2.jar
…
```
