---
title: launch
---

Like the [`fetch` command](cli-fetch.md), `launch` resolves and fetches the
artifacts of one or more dependencies. Unlike [`fetch`](cli-fetch.md), it
doesn't stop there, and proceeds to actually launch the dependencies it
fetched, like
```bash
$ coursier launch io.get-coursier:http-server_2.12:1.0.0
```

The `launch` command runs the dependencies it fetched in its own JVM. It simply
loads them in a fresh class loader, loads the main class by reflection, and
calls its main method.

## Arguments

Pass arguments to the launched program, by adding `--` after the coursier
arguments, like
```bash
$ coursier launch com.geirsson:scalafmt-cli_2.12:1.5.1 -- --version
```

## Main class

The main class to launch from the passed dependencies must be either specified
on the command-line, via the `-M` option, like
```bash
$ coursier launch com.lihaoyi:ammonite_2.12.8:1.6.0 -M ammonite.Main
```
or must be specified in the manifest of one of the loaded JARs.

If the project of the main class relies on sbt, and contains only one main
class, its manifest should already contain this main class name (it's added
out-of-the-box). In case of several main classes, one should set the
[`mainClass`](https://github.com/sbt/sbt/blob/v1.2.8/main/src/main/scala/sbt/Keys.scala#L265)
setting to the right value, for the main class to be specified in the manifest.

In case several of the loaded JARs have manifests with main classes, the
`launch` command applies an heuristic to try to find the "main" one.

If ever that heuristic fails, run the `launch` command with the `-v` option
specified twice, like
```bash
$ coursier launch com.geirsson:scalafmt-cli_2.12:1.5.1 -r bintray:scalameta/maven -v -v -- --version
…
Found main classes:
  com.martiansoftware.nailgun.NGServer (vendor: , title: )
  org.scalafmt.cli.Cli (vendor: com.geirsson, title: cli)
…
```
It should print the main classes it found. Then, replace `-v -v` with
`-M main.class.of.your.choice`, like
```bash
$ coursier launch com.geirsson:scalafmt-cli_2.12:1.5.1 -r bintray:scalameta/maven -M org.scalafmt.cli.Cli -- --version
```

## Java options

On Linux / OS X, recent versions of the coursier launcher accept Java options,
prefixed with `-J`, like
```bash
$ coursier -J-Dfoo=bar -J-Xmx2g launch com.lihaoyi:ammonite_2.12.8:1.6.0 -M ammonite.Main
Loading...
Welcome to the Ammonite Repl 1.6.0
(Scala 2.12.8 Java 1.8.0_121)
@ sys.props("foo")
res0: String = "bar"
```

Alternatively, run `java` explicitly, and pass it the options you'd like, e.g.
```bash
$ java -Dfoo=bar -Xmx2g -jar "$(which coursier)" launch com.lihaoyi:ammonite_2.12.8:1.6.0 -M ammonite.Main
Loading...
Welcome to the Ammonite Repl 1.6.0
(Scala 2.12.8 Java 1.8.0_121)
@ sys.props("foo")
res0: String = "bar"
```

From the Windows command, only the second way should work, like
```
> java -Dfoo=bar -jar /path/to/coursier launch com.lihaoyi:ammonite_2.12.8:1.6.0 -M ammonite.Main
```

## Examples

### Ammonite

```bash
$ ./coursier launch com.lihaoyi:ammonite_2.12.8:1.6.0 -M ammonite.Main
```

### Frege

```bash
$ ./coursier launch -r central -r https://oss.sonatype.org/content/groups/public \
    org.frege-lang:frege-repl-core:1.3 -M frege.repl.FregeRepl
```

### clojure

```bash
$ ./coursier launch org.clojure:clojure:1.7.0 -M clojure.main
```

### jruby

```bash
$ wget https://raw.githubusercontent.com/jruby/jruby/master/bin/jirb && \
  ./coursier launch org.jruby:jruby:9.0.4.0 -M org.jruby.Main -- -- jirb
```

### jython

```bash
$ ./coursier launch org.python:jython-standalone:2.7.0 -M org.python.util.jython
```

### Groovy

```bash
$ ./coursier launch org.codehaus.groovy:groovy-groovysh:2.4.5 -M org.codehaus.groovy.tools.shell.Main \
    commons-cli:commons-cli:1.3.1
```

### ProGuard

```bash
$ ./coursier launch net.sf.proguard:proguard-base:5.2.1 -M proguard.ProGuard
$ ./coursier launch net.sf.proguard:proguard-retrace:5.2.1 -M proguard.retrace.ReTrace
```

### Wiremock

```bash
./coursier launch com.github.tomakehurst:wiremock:1.57 -- \
--proxy-all="http://search.twitter.com" --record-mappings --verbose
```

### SQLLine

```bash
$ ./coursier launch \
  sqlline:sqlline:1.3.0 \
  org.postgresql:postgresql:42.1.4 \
  -M sqlline.SqlLine -- \
  -d org.postgresql.Driver \
  -n USERNAME \
  -p PASSWORD \
  -u jdbc:postgresql://HOST:PORT/DATABASE
```



If you wish to pass additional argument to the artifact being launched, separate them from the coursier's parameters list with the "--", just like in the Wiremock example above.
