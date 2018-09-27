---
title: launch
---

*Direct former README import (possibly not up-to-date)*

The `launch` command fetches a set of Maven coordinates it is given, along
with their transitive dependencies, then launches the "main `main` class" from
it if it can find one (typically from the manifest of the first coordinates).
The main class to launch can also be manually specified with the `-M` option.

For example, it can launch:

* [Ammonite](https://github.com/lihaoyi/Ammonite) (enhanced Scala REPL),
```
$ ./coursier launch com.lihaoyi:ammonite_2.11.8:0.7.0
```

along with the REPLs of various JVM languages like

* Frege,
```
$ ./coursier launch -r central -r https://oss.sonatype.org/content/groups/public \
    org.frege-lang:frege-repl-core:1.3 -M frege.repl.FregeRepl
```

* clojure,
```
$ ./coursier launch org.clojure:clojure:1.7.0 -M clojure.main
```

* jruby,
```
$ wget https://raw.githubusercontent.com/jruby/jruby/master/bin/jirb && \
  ./coursier launch org.jruby:jruby:9.0.4.0 -M org.jruby.Main -- -- jirb
```

* jython,
```
$ ./coursier launch org.python:jython-standalone:2.7.0 -M org.python.util.jython
```

* Groovy,
```
$ ./coursier launch org.codehaus.groovy:groovy-groovysh:2.4.5 -M org.codehaus.groovy.tools.shell.Main \
    commons-cli:commons-cli:1.3.1
```

etc.

and various programs, like

* ProGuard and its utility Retrace,
```
$ ./coursier launch net.sf.proguard:proguard-base:5.2.1 -M proguard.ProGuard
$ ./coursier launch net.sf.proguard:proguard-retrace:5.2.1 -M proguard.retrace.ReTrace
```

* Wiremock,
```
./coursier launch com.github.tomakehurst:wiremock:1.57 -- \
--proxy-all="http://search.twitter.com" --record-mappings --verbose
```

* SQLLine,

```
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
