---
title: resolve
---


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

## Tree

When passed `-t` or `--tree`, `resolve` prints a dependency
tree rather than a list:
```bash
$ cs resolve -t io.circe::circe-generic:0.12.3
  Result:
└─ io.circe:circe-generic_2.13:0.12.3
   ├─ com.chuusai:shapeless_2.13:2.3.3
   │  └─ org.scala-lang:scala-library:2.13.0
   ├─ io.circe:circe-core_2.13:0.12.3
   │  ├─ io.circe:circe-numbers_2.13:0.12.3
   │  │  └─ org.scala-lang:scala-library:2.13.0
   │  ├─ org.scala-lang:scala-library:2.13.0
   │  └─ org.typelevel:cats-core_2.13:2.0.0
   │     ├─ org.scala-lang:scala-library:2.13.0
   │     ├─ org.typelevel:cats-kernel_2.13:2.0.0
   │     │  └─ org.scala-lang:scala-library:2.13.0
   │     └─ org.typelevel:cats-macros_2.13:2.0.0
   │        └─ org.scala-lang:scala-library:2.13.0
   └─ org.scala-lang:scala-library:2.13.0
```

Each dependency has its own direct dependencies as children, which
themselves have their own dependencies as children, this way recursively.

## Reverse tree

When passed `-T` or `--reverse-tree`, `resolve` prints trees
the other way around,
going from a dependency to its dependees, allowing to spot
which dependencies are bringing another one in:
```bash
$ cs resolve -T io.circe::circe-generic:0.12.3
  Result:
├─ com.chuusai:shapeless_2.13:2.3.3
│  └─ io.circe:circe-generic_2.13:0.12.3
├─ io.circe:circe-core_2.13:0.12.3
│  └─ io.circe:circe-generic_2.13:0.12.3
├─ io.circe:circe-generic_2.13:0.12.3
├─ io.circe:circe-numbers_2.13:0.12.3
│  └─ io.circe:circe-core_2.13:0.12.3
│     └─ io.circe:circe-generic_2.13:0.12.3
├─ org.scala-lang:scala-library:2.13.0
│  ├─ com.chuusai:shapeless_2.13:2.3.3
│  │  └─ io.circe:circe-generic_2.13:0.12.3
…
```

## What depends on

When there are many transitive dependencies, these reverse trees can
become hard to read. `--what-depends-on` allows to focus
on one particular dependency, like
```bash
$ cs resolve io.circe::circe-generic:0.12.3 \
    --what-depends-on org.typelevel:cats-core_2.13
  Result:
└─ org.typelevel:cats-core_2.13:2.0.0
   └─ io.circe:circe-core_2.13:0.12.3
      └─ io.circe:circe-generic_2.13:0.12.3
```
This reads like "`cats-core` is brought by `circe-core`, itself
brought by `circe-generic` (root dependency)".
