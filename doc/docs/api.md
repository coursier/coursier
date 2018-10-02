---
title: Usage
---

*Direct former README import (possibly not up-to-date)*

Add to your `build.sbt`
```scala
libraryDependencies ++= Seq(
  "io.get-coursier" %% "coursier" % "@VERSION@",
  "io.get-coursier" %% "coursier-cache" % "@VERSION@"
)
```

The first module, `"io.get-coursier" %% "coursier" % "@VERSION@"`, contains among others,
definitions,
mainly in [`Definitions.scala`](https://github.com/coursier/coursier/blob/master/core/shared/src/main/scala/coursier/core/Definitions.scala),
[`Resolution`](https://github.com/coursier/coursier/blob/master/core/shared/src/main/scala/coursier/core/Resolution.scala), representing a particular state of the resolution,
and [`ResolutionProcess`](https://github.com/coursier/coursier/blob/master/core/shared/src/main/scala/coursier/core/ResolutionProcess.scala),
that expects to be given metadata, wrapped in any `Monad`, then feeds these to `Resolution`, and at the end gives
you the final `Resolution`, wrapped in the same `Monad` it was given input. This final `Resolution` has all the dependencies,
including the transitive ones.

The second module, `"io.get-coursier" %% "coursier-cache" % "@VERSION@"`, is precisely in charge of fetching
these input metadata. It uses its own `coursier.util.Task` as a `Monad` to wrap them. It also fetches artifacts (JARs, etc.).
It caches all of these (metadata and artifacts) on disk, and validates checksums too.

In the code below, we'll assume some imports are around,
```scala mdoc:silent
import coursier._
```


Resolving dependencies involves create an initial resolution state, with all the initial dependencies in it, like
```scala mdoc:silent
val start = Resolution(
  Set(
    Dependency(
      Module("org.typelevel", "cats-core_2.11"), "0.6.0"
    ),
    Dependency(
      Module("org.scalaz", "scalaz-core_2.11"), "7.2.3"
    )
  )
)
```

It goes without saying that a `Resolution` is immutable, as are all the classes defined in the core module.
The resolution process will go on by giving successive `Resolution`s, until the final one.

`start` above is only the initial state - it is far from over, as the `isDone` method on it tells,
```scala mdoc
start.isDone
```

```scala mdoc:passthrough
assert(!start.isDone)
```

In order for the resolution to go on, we'll need things from a few repositories,
```scala mdoc:silent
val repositories = Seq(
  Cache.ivy2Local,
  MavenRepository("https://repo1.maven.org/maven2")
)
```
The first one, `Cache.ivy2Local`, is defined in `coursier.Cache`, itself from the `coursier-cache` module that
we added above. It is an `IvyRepository`, picking things under `~/.ivy2/local`. An `IvyRepository`
is related to the [Ivy](http://ant.apache.org/ivy/) build tool. This kind of repository involves a so-called [pattern](http://ant.apache.org/ivy/history/2.4.0/concept.html#patterns), with
various properties. These are not of very common use in Scala, although SBT uses them a bit.

The second repository is a `MavenRepository`. These are simpler than the Ivy repositories. They're the ones
we're the most used to in Scala. Common ones like [Central](https://repo1.maven.org/maven2) like here, or the repositories
from [Sonatype](https://oss.sonatype.org/content/repositories/), are Maven repositories. These originate
from the [Maven](https://maven.apache.org/) build tool. Unlike the Ivy repositories which involve customisable patterns to point
to the underlying metadata and artifacts, the paths of these for Maven repositories all look alike,
like for any particular version of the standard library, under paths like
[this one](http://repo1.maven.org/maven2/org/scala-lang/scala-library/2.11.7/).

Both `IvyRepository` and `MavenRepository` are case classes, so that it's straightforward to specify one's own
repositories.

To set credentials for a `MavenRepository` or `IvyRepository`, set their `authentication` field, like
```scala mdoc:silent
import coursier.core.Authentication

MavenRepository(
  "https://nexus.corp.com/content/repositories/releases",
  authentication = Some(Authentication("user", "pass"))
)
```

Now that we have repositories, we're going to mix these with things from the `coursier-cache` module,
for resolution to happen via the cache. We'll create a function
of type `Seq[(Module, String)] => F[Seq[((Module, String), Either[Seq[String], (Artifact.Source, Project)])]]`.
Given a sequence of dependencies, designated by their `Module` (organisation and name in most cases)
and version (just a `String`), it gives either errors (`Seq[String]`) or metadata (`(Artifact.Source, Project)`),
wrapping the whole in a monad `F`.
```scala mdoc:silent
import coursier.util.Task

val fetch = Fetch.from(repositories, Cache.fetch[Task]())
```

The monad used by `Fetch.from` is `coursier.util.Task`, but the resolution process is not tied to a particular
monad - any stack-safe monad would do.

With this `fetch` method, we can now go on with the resolution. Calling `process` on `start` above gives a
[`ResolutionProcess`](https://github.com/coursier/coursier/blob/master/core/shared/src/main/scala/coursier/core/ResolutionProcess.scala),
that drives the resolution. It is loosely inspired by the `Process` of [fs2](http://fs2.io/) (formerly scalaz-stream).
It is an immutable structure, that represents the various states the resolution process can be in.

Its method `current` gives the current `Resolution`. Calling `isDone` on the latter says whether the
resolution is done or not.

The `next` method, that expects a `fetch` method like the one above, gives
the "next" state of the resolution process, wrapped in the monad of the `fetch` method. It allows to do
one resolution step.

Lastly, the `run` method runs the whole resolution until its end. It expects a `fetch` method too,
and will make at most `maxIterations` steps (50 by default), and return the "final" resolution state,
wrapped in the monad of `fetch`. One should check that the `Resolution` it returns is done (`isDone`) -
the contrary means that `maxIterations` were reached, likely signaling an issue, unless the underlying
resolution is particularly complex, in which case `maxIterations` could be increased.

Let's run the whole resolution,
```scala mdoc:silent
import scala.concurrent.ExecutionContext.Implicits.global

val resolution = start.process.run(fetch).unsafeRun()
```

To get additional feedback during the resolution, we can give the `Cache.fetch` method above
a [`Cache.Logger`](https://github.com/coursier/coursier/blob/cf269c6895e19f2d590f08811406724304332950/cache/src/main/scala/coursier/Cache.scala#L484-L490).

By default, downloads happen in a global fixed thread pool (with 6 threads, allowing for 6 parallel downloads), but
you can supply your own thread pool to `Cache.fetch`.

Now that the resolution is done, we can check for errors in
```scala mdoc:silent
val errors: Seq[((Module, String), Seq[String])] = resolution.metadataErrors
```
These would mean that the resolution wasn't able to get metadata about some dependencies.

We can also check for version conflicts, in
```scala mdoc:silent
val conflicts: Set[Dependency] = resolution.conflicts
```
which are dependencies whose versions could not be unified.

Then, if all went well, we can fetch and get local copies of the artifacts themselves (the JARs) with
```scala mdoc:silent
import java.io.File
import coursier.util.Gather

val localArtifacts: Seq[Either[FileError, File]] =
  Gather[Task].gather(
    resolution.artifacts.map(Cache.file[Task](_).run)
  ).unsafeRun()
```

We're using the `Cache.file` method, that can also be given a `Logger` (for more feedback) and a custom thread pool.
