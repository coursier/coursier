---
title: API
---

> This page describes the new high level API of coursier, being added since
`1.1.0-M10`. It's still subject to source / binary compatibility breakages,
even though its general outline shouldn't change much. See [low level API](api-low-level.md) for the former API, less subject to change.

The high level API of coursier aims at being as simple to use as its
[CLI](cli-overview.md), with sensible defaults in particular, while retaining
the purity of the low-level API.

It exposes a few objects under the `coursier` namespace, most notably
`coursier.Resolve` and `coursier.Fetch`, allowing to resolve dependencies
and fetch their artifacts.

To get started with the high level API, add the settings below to `build.sbt`
```scala
// build.sbt
@EXTRA_SBT@libraryDependencies ++= Seq(
  "io.get-coursier" %% "coursier" % "@VERSION@",
)
```

## Resolve

`coursier.Resolve` allows to run resolutions, that is
finding all the transitive dependencies of some initial dependencies (while
reconciling their versions at the same time).

This entirely relies on metadata, that is POM or `maven-metadata.xml` files
for Maven repositories, and `ivy.xml` files for Ivy repositories.

If you're fine with all the defaults (cache location,
resolution parameters, …), you can just resolve some dependencies with
```scala mdoc:silent
import coursier._

val resolution = Resolve()
  .addDependencies(dep"org.tpolecat:doobie-core_2.12:0.6.0")
  .run()
```

```scala mdoc:passthrough
resolution: Resolution
```

This runs the resolution synchronously.
To run resolutions in the background, the `future` and
`io` methods can be called instead of `run`, to start resolution
eagerly via `scala.concurrent.Future`, or lazily via an IO monad.

`resolution` is a `coursier.core.Resolution`, which represents the
(final) state of the resolution. It has various methods allowing to
process dependencies, most notably `minDependencies`, returning a
set of dependencies (with redundant dependencies stripped).

The object returned by `Resolve()` allows to optionally change a number of
parameters, discussed below.

### Dependencies

Pass some extra dependencies with `addDependencies`. `addDependencies`
accepts multiple dependencies, and can be called several times. If you prefer,
you can also call `withDependencies`, that accepts a `Seq[Dependency]`, and
wipes any previously passed dependency.

```scala mdoc:silent:reset
import coursier._

val resolution = Resolve()
  .addDependencies(
    dep"org.http4s:http4s-dsl_2.12:0.18.22",
    dep"org.tpolecat:doobie-core_2.12:0.6.0"
  )
  .run()
```

### Repositories

The default repositories are the Ivy2 local repository (only on the JVM)
and [Maven Central](https://repo1.maven.org/maven2). More details
[here](other-repositories.md). One can add some extra repositories
with `addRepositories`, that accepts multiple arguments, and can be called
several times. Optionally, one can call `withRepositories` to set all the
repositories at once, wiping the default ones and any previously passed one.

```scala mdoc:silent:reset
import coursier._

val resolution = Resolve()
  .addDependencies(dep"sh.almond:scala-kernel-api_2.12.8:0.2.2")
  .addRepositories(
    Repositories.sonatype("releases"),
    Repositories.jitpack
  )
  .run()
```

### Cache

One can override the default cache, and adjust its parameters, with
`withCache`,
```scala mdoc:silent:reset
import coursier._
import coursier.cache._
import coursier.cache.loggers._

import scala.concurrent.duration._

val pool = java.util.concurrent.Executors.newFixedThreadPool(3)

val cache = FileCache()
  .withLocation("./path/to/custom-cache")
  .withLogger(RefreshLogger.create(System.out))
  .withPool(pool)
  .withTtl(1.hour)

val resolution = Resolve()
  .addDependencies(dep"org.tpolecat:doobie-core_2.12:0.6.0")
  .withCache(cache)
  .run()

pool.shutdown()
```

### Resolution parameters

A number of resolution parameters can be adjusted by passing a custom
`ResolutionParams` to `withResolutionParameters`,
```scala mdoc:silent:reset
import coursier._
import coursier.params._
import coursier.params.rule._

val params = ResolutionParams()
  .withMaxIterations(50)
  .withScalaVersion("2.12.8")
  .addForceVersion(
    mod"joda-time:joda-time" -> "2.10.1"
  )
  .addRule(
    SameVersion(mod"com.fasterxml.jackson.*:jackson-*")
  )

val resolution = Resolve()
  .addDependencies(dep"org.apache.spark:spark-sql_2.12:2.4.0")
  .withResolutionParams(params)
  .run()
```

### Future

If you prefer to eagerly run resolutions in the background via a `Future`,
call `future` instead of `run`, like
```scala mdoc:silent:reset
import coursier._

val futureResolution = Resolve()
  .addDependencies(dep"org.tpolecat:doobie-core_2.12:0.6.0")
  .future()
```

```scala mdoc:passthrough
futureResolution: scala.concurrent.Future[Resolution]
```

### IO

If you prefer resolutions to be run lazily in the background via an IO monad,
call `io` instead of `run` or `future`, like
```scala mdoc:silent:reset
import coursier._

val ioResolution = Resolve()
  .addDependencies(dep"org.tpolecat:doobie-core_2.12:0.6.0")
  .io
```

```scala mdoc:passthrough
ioResolution: coursier.util.Task[Resolution]
```

The default IO monad is the rather basic `coursier.util.Task`, that ships with
coursier. This monad can be changed by changing the cache _upfront_, like
```scala mdoc:silent:reset
import coursier._
import coursier.cache._
import coursier.interop.cats._

import cats.effect.IO
import scala.concurrent.ExecutionContext

implicit val cs = IO.contextShift(ExecutionContext.global)

val cache = FileCache[IO]()

val ioResolution = Resolve(cache) // note the cache passed here
  .addDependencies(dep"org.tpolecat:doobie-core_2.12:0.6.0")
  .io
```

```scala mdoc:passthrough
ioResolution: cats.effect.IO[Resolution]
```

Note that this example requires the `coursier-cats-interop` module
(`io.get-coursier::coursier-cats-interop:@VERSION@`).

## Fetch

`coursier.Fetch` resolves dependencies, then fetches their artifacts.

Use it like
```scala mdoc:silent:reset
import coursier._

val files = Fetch()
  .addDependencies(dep"org.tpolecat:doobie-core_2.12:0.6.0")
  .run()

// files: Seq[java.io.File]
```

```scala mdoc:passthrough
files: Seq[java.io.File]
```

One can
- [add dependencies](#dependencies),
- [adjust repositories](#repositories),
- [adjust the cache](#cache), or
- [change the resolution parameters](#resolution-parameters),

with the same methods as [`coursier.Resolve`](#resolve).

### Classifiers

Call `addClassifiers` to adjust the classifiers you're interested in, like
```scala mdoc:silent:reset
import coursier._

val files = Fetch()
  .addDependencies(dep"org.tpolecat:doobie-core_2.12:0.6.0")
  .addClassifiers(Classifier.javadoc, Classifier.sources)
  .run()
```

To keep default artifacts while also fetching classifiers, call
`withMainArtifacts`, like
```scala mdoc:silent:reset
import coursier._

val files = Fetch()
  .addDependencies(dep"org.tpolecat:doobie-core_2.12:0.6.0")
  .addClassifiers(Classifier.javadoc, Classifier.sources)
  .withMainArtifacts()
  .run()
```

### Artifact types

If you know what you're doing and want to fetch all or some specific artifact
types, call `allArtifactTypes` or `addArtifactType`, like
```scala mdoc:silent:reset
import coursier._

val files = Fetch()
  .addDependencies(dep"org.tpolecat:doobie-core_2.12:0.6.0")
  .allArtifactTypes()
  .run()
```

```scala mdoc:silent:reset
import coursier._

val files = Fetch()
  .addDependencies(dep"org.tpolecat:doobie-core_2.12:0.6.0")
  .addArtifactTypes(Type.pom, Type.testJar)
  .run()
```

If none of these methods are called, artifact types are automatically adjusted
depending on [classifiers](#classifiers).

### Future

Like for [`Resolve`](#future), fetching can be (eagerly) run in the background
by calling `future` rather than `run`, like

```scala mdoc:silent:reset
import coursier._

val futureFiles = Fetch()
  .addDependencies(dep"org.tpolecat:doobie-core_2.12:0.6.0")
  .future()
```

```scala mdoc:passthrough
futureFiles: scala.concurrent.Future[Seq[java.io.File]]
```

### IO

Like for [`Resolve`](#io), fetching can be (lazily) run in the background
by calling `io` rather than `run`, like

```scala mdoc:silent:reset
import coursier._

val ioFiles = Fetch()
  .addDependencies(dep"org.tpolecat:doobie-core_2.12:0.6.0")
  .io
```

```scala mdoc:passthrough
ioFiles: coursier.util.Task[Seq[java.io.File]]
```

Here too, the default IO monad is `coursier.util.Task` that ships with coursier.

One can use another IO monad by adjusting the cache _upfront_, like
```scala mdoc:silent:reset
import coursier._
import coursier.cache._
import coursier.interop.cats._

import cats.effect.IO
import scala.concurrent.ExecutionContext

implicit val cs = IO.contextShift(ExecutionContext.global)

val cache = FileCache[IO]()

val ioFiles = Fetch(cache) // note the cache passed here
  .addDependencies(dep"org.tpolecat:doobie-core_2.12:0.6.0")
  .io
```

```scala mdoc:passthrough
ioFiles: cats.effect.IO[Seq[java.io.File]]
```

Note that this example requires the `coursier-cats-interop` module
(`io.get-coursier::coursier-cats-interop:@VERSION@`).
