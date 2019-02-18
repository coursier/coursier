---
title: API
---

> This page describes the new high level API of coursier, being added since
`1.1.0-M10`. It's still subject to source / binary compatibility breakages,
even though its general outline shouldn't change much.

The high level API of coursier aims at being as simple to use as its
[CLI](cli-overview.md), with sensible defaults in particular, while retaining
the purity of the low-level API.

It exposes a few objects under the `coursier` namespace, most notably
`coursier.Resolve` and `coursier.Fetch`, allowing to resolve dependencies
and fetch their artifacts.

## Resolve

The `coursier.Resolve` object exposes methods to run resolutions, that is
finding all the transitive dependencies of some intial dependencies (while
reconciling their versions at the same time).

This entirely relies on metadata, that is POM or `maven-metadata.xml` files
for Maven repositories, and `ivy.xml` files for Ivy repositories.

If you're fine with all the defaults (cache location,
resolution parameters, …), you can just resolve some dependencies with
```scala mdoc:silent
import coursier._

val resolution = Resolve.resolve(
  Seq(dep"org.tpolecat:doobie-core_2.12:0.6.0")
)
```

```scala mdoc:passthrough
resolution: Resolution
```

This runs the resolution synchronously. The `resolveFuture` and
`resolveIO` methods allow to run resolutions in the background, either
eagerly via `scala.concurrent.Future`, or lazily via an IO monad.

`resolution` is a `coursier.core.Resolution`, which represents the
(final) state of the resolution. It has various methods allowing to
process dependencies, most notably `minDependencies`, returning a
set of dependencies (with redundant dependencies stripped).

The `resolve*` methods accept a number of optional parameters, discussed below.

### Repositories

The default repositories are the Ivy2 local repository (only on the JVM)
and [Maven Central](https://repo1.maven.org/maven2). More details
[here](other-repositories.md). These can be overridden by passing
a `repositories` parameter, like
```scala mdoc:silent:reset
import coursier._
import coursier.util.Repositories

val resolution = Resolve.resolve(
  Seq(dep"sh.almond:scala-kernel-api_2.12.8:0.2.2"),
  repositories = Resolve.defaultRepositories :+ Repositories.jitpack
)
```

## Philosophy

Most methods in the `coursier.Resolve` and `coursier.Fetch` objects
should be referentially transparent. At the
same time, in order to make them more convenient to use, plenty of their inputs
have sensible default values. These default values may in turn, trigger
some side-effects, like checking if some directories exist (to find the cache
location), starting thread pools (to later download things from), or checking
some environment variables and Java properties. If you pass all these
methods inputs explicitly, not relying on default values at all, you'll recover
referential transparency.

Some methods download things, read from and write to the cache, that is
perform some I/O.
They return a value wrapped in an IO monad, `coursier.util.Task` by default.
These methods also have sibling methods, either returning
a `scala.concurrent.Future`, or returning the underlying value synchronously,
so that one can just ignore any IO monad consideration if they want to.
Also, any IO monad with a `coursier.util.Schedulable` instance can be used
instead of the (minimalist and probably low end) `coursier.util.Task`.
The `cats-interop` and `scalaz-interop` have such instances for cats-effect and
scalaz 7.2.x.
