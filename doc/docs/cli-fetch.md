---
title: fetch
---

*Direct former README import (possibly not up-to-date)*

The `fetch` command simply fetches a set of dependencies, along with their
transitive dependencies, then prints the local paths of all their artifacts.

Example
```
$ ./coursier fetch org.apache.spark:spark-sql_2.11:1.6.1
/path/to/.coursier/cache/v1/https/repo1.maven.org/maven2/org/apache/hadoop/hadoop-annotations/2.2.0/hadoop-annotations-2.2.0.jar
/path/to/.coursier/cache/v1/https/repo1.maven.org/maven2/org/tukaani/xz/1.0/xz-1.0.jar
/path/to/.coursier/cache/v1/https/repo1.maven.org/maven2/org/tachyonproject/tachyon-underfs-s3/0.8.2/tachyon-underfs-s3-0.8.2.jar
/path/to/.coursier/cache/v1/https/repo1.maven.org/maven2/org/glassfish/grizzly/grizzly-http/2.1.2/grizzly-http-2.1.2.jar
...
```

By adding the `-p` option, these paths can be handed over directly to
`java -cp`, like
```
$ java -cp "$(./coursier fetch -p com.lihaoyi:ammonite_2.11.8:0.7.0)" ammonite.Main
Loading...
Welcome to the Ammonite Repl 0.7.0
(Scala 2.11.8 Java 1.8.0_60)
@
```

Fetch with module level attributes, as opposed to e.g. `--classifier` is applied globally.
```
$ ./coursier fetch org.apache.avro:avro:1.7.4,classifier=tests --artifact-type test-jar,jar
```

Fetch and generate a machine readable json report. [Json Report Documentation](json-report.md)
```
$ ./coursier fetch org.apache.avro:avro:1.7.4 --json-output-file report.json
```
