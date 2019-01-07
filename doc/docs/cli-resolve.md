---
title: resolve
---

The `resolve` command resolves then prints the transitive dependencies of one
or more dependencies.

Use like
```bash
$ coursier resolve org.http4s:http4s-dsl_2.12:0.18.21
co.fs2:fs2-core_2.12:0.10.6:default
co.fs2:fs2-io_2.12:0.10.6:default
co.fs2:fs2-scodec_2.12:0.10.6:default
org.http4s:http4s-core_2.12:0.18.21:default
org.http4s:http4s-dsl_2.12:0.18.21:default
org.http4s:http4s-websocket_2.12:0.2.1:default
org.http4s:parboiled_2.12:1.0.0:default
org.log4s:log4s_2.12:1.6.1:default
org.scala-lang:scala-library:2.12.7:default
org.scala-lang:scala-reflect:2.12.6:default
org.scodec:scodec-bits_2.12:1.1.6:default
org.slf4j:slf4j-api:1.7.25:default
org.typelevel:cats-core_2.12:1.4.0:default
org.typelevel:cats-effect_2.12:0.10.1:default
org.typelevel:cats-kernel_2.12:1.4.0:default
org.typelevel:cats-macros_2.12:1.4.0:default
org.typelevel:machinist_2.12:0.6.5:default
```

The `resolve` command only relies on metadata, that is `*.pom` or `*.xml` files.
It doesn't fetch or check the actual artifacts (`*.jar` files typically).
If you're interested in the `.jar` files or other artifacts, see the
[`fetch` command](cli-fetch.md) instead.

## Tree

The default output of the `resolve` command is a simple listing of dependencies,
currently sorted by organization, then name. When passed the `--tree` or `-t`
option, it outputs a tree instead, like
```bash
$ coursier resolve -t com.github.alexarchambault:case-app_2.12:2.0.0-M5
  Result:
└─ com.github.alexarchambault:case-app_2.12:2.0.0-M5
   ├─ com.github.alexarchambault:case-app-annotations_2.12:2.0.0-M5
   │  └─ org.scala-lang:scala-library:2.12.7
   ├─ com.github.alexarchambault:case-app-util_2.12:2.0.0-M5
   │  ├─ com.chuusai:shapeless_2.12:2.3.3
   │  │  ├─ org.scala-lang:scala-library:2.12.4 -> 2.12.7
   │  │  └─ org.typelevel:macro-compat_2.12:1.1.1
   │  │     └─ org.scala-lang:scala-library:2.12.0 -> 2.12.7
   │  └─ org.scala-lang:scala-library:2.12.7
   └─ org.scala-lang:scala-library:2.12.7
```

In this tree, each dependency has its own direct dependencies as children, which
themselves have their own dependencies as children, this way recursively.

## Reverse tree

Trees can also be printed the other way around, with dependees as children, via
the `--reverse-tree` or `-T` options, like
```bash
$ coursier resolve -T com.chuusai:shapeless_2.12:2.3.3
  Result:
├─ com.chuusai:shapeless_2.12:2.3.3
├─ org.scala-lang:scala-library:2.12.4
│  ├─ com.chuusai:shapeless_2.12:2.3.3
│  └─ org.typelevel:macro-compat_2.12:1.1.1 org.scala-lang:scala-library:2.12.0 -> 2.12.4
│     └─ com.chuusai:shapeless_2.12:2.3.3
└─ org.typelevel:macro-compat_2.12:1.1.1
   └─ com.chuusai:shapeless_2.12:2.3.3
```

## What depends on

The printed trees can quickly grow in size (try `coursier resolve -T org.apache.spark:spark-sql_2.12:2.4.0`). If you're interested in knowing what brings a
particular dependency, use the `--what-depends-on` option, like
```bash
$ coursier resolve org.apache.spark:spark-sql_2.12:2.4.0 \
    --what-depends-on org.htrace:htrace-core
  Result:
└─ org.htrace:htrace-core:3.0.4
   ├─ org.apache.hadoop:hadoop-common:2.6.5
   │  └─ org.apache.hadoop:hadoop-client:2.6.5
   │     └─ org.apache.spark:spark-core_2.12:2.4.0
   │        ├─ org.apache.spark:spark-catalyst_2.12:2.4.0
   │        │  └─ org.apache.spark:spark-sql_2.12:2.4.0
   │        └─ org.apache.spark:spark-sql_2.12:2.4.0
   └─ org.apache.hadoop:hadoop-hdfs:2.6.5
      └─ org.apache.hadoop:hadoop-client:2.6.5
         └─ org.apache.spark:spark-core_2.12:2.4.0
            ├─ org.apache.spark:spark-catalyst_2.12:2.4.0
            │  └─ org.apache.spark:spark-sql_2.12:2.4.0
            └─ org.apache.spark:spark-sql_2.12:2.4.0
```

`--what-depends-on` can be specified multiple times, like
```bash
$ coursier resolve org.apache.spark:spark-sql_2.12:2.4.0 \
    --what-depends-on org.htrace:htrace-core \
    --what-depends-on org.json4s:json4s-jackson_2.12
  Result:
├─ org.htrace:htrace-core:3.0.4
│  ├─ org.apache.hadoop:hadoop-common:2.6.5
│  │  └─ org.apache.hadoop:hadoop-client:2.6.5
│  │     └─ org.apache.spark:spark-core_2.12:2.4.0
│  │        ├─ org.apache.spark:spark-catalyst_2.12:2.4.0
│  │        │  └─ org.apache.spark:spark-sql_2.12:2.4.0
│  │        └─ org.apache.spark:spark-sql_2.12:2.4.0
│  └─ org.apache.hadoop:hadoop-hdfs:2.6.5
│     └─ org.apache.hadoop:hadoop-client:2.6.5
│        └─ org.apache.spark:spark-core_2.12:2.4.0
│           ├─ org.apache.spark:spark-catalyst_2.12:2.4.0
│           │  └─ org.apache.spark:spark-sql_2.12:2.4.0
│           └─ org.apache.spark:spark-sql_2.12:2.4.0
└─ org.json4s:json4s-jackson_2.12:3.5.3
   └─ org.apache.spark:spark-core_2.12:2.4.0
      ├─ org.apache.spark:spark-catalyst_2.12:2.4.0
      │  └─ org.apache.spark:spark-sql_2.12:2.4.0
      └─ org.apache.spark:spark-sql_2.12:2.4.0
```
