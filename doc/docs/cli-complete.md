---
title: complete
---

`complete` allows one to complete _Maven_ coordinates.

It can answer questions such as:

- What artifacts are published under a given _Maven_ _groupId_, for example `org.scala-lang` or `io.circe`?
- For a given _Maven_ _groupId_ and _artifactId_, which version(s) of the artifact are available?

The `complete` command takes a single argument which is composed of up to 3 colon-separated components.

`cs complete groupId:artifactId:version`

Let's demonstrate this with a few examples.

## Looking-up a _Maven_ _groupId_

```bash
$ cs complete org.scala-lang
org.scala-lang
org.scala-lang-osgi

$ cs complete com.zax
com.zaxxer

$ cs complete com.typesafe.
com.typesafe.akka
com.typesafe.conductr
com.typesafe.config
com.typesafe.genjavadoc
com.typesafe.jse_2.10
com.typesafe.jse_2.11

<elided>

com.typesafe.ssl-config-core_2.13.0-RC3
com.typesafe.webdriver_2.10
com.typesafe.zinc
```

As can be seen from these 3 examples, there are two _Maven_ _groupIds_ that share the common `org.scala-lang` prefix whereas `com.zax` has exactly one matching _groupId_. Finally, `cs complete com.typesafe.` will list more than 80 matching _groupIds_.

We can now focus on searching for some artifacts under these _groupIds_.

## Looking-up artifacts for a given _groupId_

Passing a _Maven_ _groupId_ terminated by a colon will trigger a lookup of all artifacts published under that _groupId_.

For example:

```bash
$ cs complete com.zaxxer:
HikariCP
HikariCP-agent
HikariCP-java6
HikariCP-java7
HikariCP-java9ea
HikariCP-parent
SansOrm
SparseBitSet
influx4j
jnb-ping
kuill
nuprocess
sansorm

$ cs complete com.typesafe.akka:
akka
akka-actor-testkit-typed_2.11
akka-actor-testkit-typed_2.12
akka-actor-testkit-typed_2.13
akka-actor-testkit-typed_2.13.0-M3
akka-actor-testkit-typed_2.13.0-M5
akka-actor-testkit-typed_2.13.0-RC1
akka-actor-testkit-typed_2.13.0-RC2
akka-actor-testkit-typed_2.13.0-RC3
akka-actor-tests_2.10
akka-actor-tests_2.10.0-M7
akka-actor-tests_2.10.0-RC1
akka-actor-tests_2.10.0-RC2
akka-actor-tests_2.10.0-RC3
akka-actor-tests_2.10.0-RC5
akka-actor-tests_2.11
akka-actor-tests_2.11.0-M3
akka-actor-tests_2.11.0-RC3
akka-actor-tests_2.11.0-RC4
akka-actor-tests_2.12.0-M1
akka-actor-typed_2.11
akka-actor-typed_2.12
akka-actor-typed_2.13
akka-actor-typed_2.13.0-M3
akka-actor-typed_2.13.0-M5
akka-actor-typed_2.13.0-RC1
akka-actor-typed_2.13.0-RC2
akka-actor-typed_2.13.0-RC3

<elided>

akka-zeromq_2.11
akka-zeromq_2.11.0-M3
hello-kernel_2.10
hello-kernel_2.11
```

An interesting observation that can be made when looking at the output for `cs complete com.typesafe.akka:` is that each artifact is published against one or more minor versions of _Scala_ (e.g. `2.11`, `2.12`, `2.13`, etc.). This is because these are _Scala_ libraries that are cross-built for each _Scala_ version supported by the library.

> Note: _Scala_ guarantees binary compatibility between patch releases with a minor release.

For example, if we take the `akka-actor-typed` library:

```bash
$ cs complete com.typesafe.akka:akka-actor-typed
akka-actor-typed_2.11
akka-actor-typed_2.12
akka-actor-typed_2.13
akka-actor-typed_2.13.0-M3
akka-actor-typed_2.13.0-M5
akka-actor-typed_2.13.0-RC1
akka-actor-typed_2.13.0-RC2
akka-actor-typed_2.13.0-RC3
```

We see that it was published against 3 _Scala_ releases (ignoring Milestone releases and Release Candidates).

## Looking-up artifact versions

Given a _Maven_ _groupId_ and _artifactId_, we can use `cs complete` to look up which version[s] of the artifact have been published.

We'll take two examples, one for a _Java_ library and one for a _Scala_ Library.

Let's start with the `HikariCP` library.

```bash
$ cs complete com.zaxxer:HikariCP:
1.1.3
1.1.4

<elided>

2.7.8
2.7.9
3.0.0
3.1.0
3.2.0
3.3.0
3.3.1
3.4.0
3.4.1
3.4.2
3.4.3
3.4.4
3.4.5
```

Pretty straightforward...

Let's look at the `akka-actor-typed` library published for _Scala_ 2.13.

```bash
$ cs complete com.typesafe.akka:akka-actor-typed_2.13:
2.5.23
2.5.24
2.5.25
2.5.26
2.5.27
2.5.28
2.5.29
2.5.30
2.5.31
2.5.32
2.6.0-M3
2.6.0-M4
2.6.0-M5
2.6.0-M6
2.6.0-M7
2.6.0-M8
2.6.0-RC1
2.6.0-RC2
2.6.0
2.6.1
2.6.2
2.6.3
2.6.4
2.6.5
2.6.6
2.6.7
2.6.8
2.6.9
2.6.10
```

In conclusion we can say that `cs complete` is a very powerful command that allows us to easily look-up library (artifact) version information from the command line.