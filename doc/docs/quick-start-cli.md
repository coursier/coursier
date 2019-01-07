---
title: Command-line
---

## Installation

Download and run its launcher with
```bash
$ curl -L -o coursier https://git.io/coursier &&
    chmod +x coursier &&
    ./coursier --help
```

See [the dedicated page](cli-intro.md) for other installation methods.

## Running an application

Run an application distributed via artifacts with e.g.
```bash
$ ./coursier launch com.lihaoyi:ammonite_2.11.8:1.0.3
```

## List artifacts

Download and list the classpath of one or several dependencies with e.g.
```bash
$ ./coursier fetch \
    org.apache.spark:spark-sql_2.11:1.6.1 \
    com.twitter:algebird-spark_2.11:0.12.0
/path/to/.cache/coursier/v1/https/repo1.maven.org/maven2/com/twitter/algebird-spark_2.11/0.12.0/algebird-spark_2.11-0.12.0.jar
/path/to/.cache/coursier/v1/https/repo1.maven.org/maven2/com/twitter/algebird-core_2.11/0.12.0/algebird-core_2.11-0.12.0.jar
/path/to/.cache/coursier/v1/https/repo1.maven.org/maven2/org/apache/hadoop/hadoop-annotations/2.2.0/hadoop-annotations-2.2.0.jar
/path/to/.cache/coursier/v1/https/repo1.maven.org/maven2/org/tukaani/xz/1.0/xz-1.0.jar
...
```

See [the dedicated page](cli-intro.md) for more features and examples.
