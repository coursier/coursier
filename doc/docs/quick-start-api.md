---
title: API
---

Add to your `build.sbt`
```scala
@EXTRA_SBT@libraryDependencies ++= Seq(
  "io.get-coursier" %% "coursier" % "@VERSION@",
  "io.get-coursier" %% "coursier-cache" % "@VERSION@"
)
```

Add an import for coursier,
```scala mdoc:silent
import coursier._
```

```scala mdoc:passthrough
object LocalRepositories {
  val ivy2LocalIsIvy = coursier.cache.LocalRepositories.ivy2Local match {
    case _: coursier.ivy.IvyRepository => true
    case _ => false
  }

  assert(ivy2LocalIsIvy)

  // The goal of this is to make the printed ivy2Local below more anonymous,
  // with literally ${user.home} in it rather than the current home dir.
  // ${user.home} could have been used in the definition of ivy2Local itself,
  // but it would then have required properties, which would have cluttered
  // output here.

  import coursier.ivy.Pattern.Chunk, Chunk._

  val ivy2Local = coursier.ivy.IvyRepository.fromPattern(
    coursier.ivy.Pattern(
      Seq[Chunk]("file://", Var("user.home"), "/local/") ++ coursier.ivy.Pattern.default.chunks
    ),
    dropInfoAttributes = true
  )
}
```

To resolve dependencies, first create a `Resolution` case class with your dependencies in it,
```scala mdoc:silent
val start = Resolution(
  Seq(
    Dependency(
      Module(org"org.scalaz", name"scalaz-core_2.11"), "7.2.3"
    ),
    Dependency(
      Module(org"org.typelevel", name"cats-core_2.11"), "0.6.0"
    )
  )
)
```

Create a fetch function able to get things from a few repositories via the local cache,
```scala mdoc:passthrough
import coursier.cache.Cache

val repositories = Seq(
  LocalRepositories.ivy2Local,
  MavenRepository("https://repo1.maven.org/maven2")
)

val fetch = ResolutionProcess.fetch(repositories, Cache.default.fetch)
```

```scala
import coursier.cache.Cache
import coursier.cache.LocalRepositories

val repositories = Seq(
  LocalRepositories.ivy2Local,
  MavenRepository("https://repo1.maven.org/maven2")
)

val fetch = ResolutionProcess.fetch(repositories, Cache.default.fetch)
```

Then run the resolution per-se,
```scala mdoc:silent
import scala.concurrent.ExecutionContext.Implicits.global

val resolution = start.process.run(fetch).unsafeRun()
```
That will fetch and use metadata.

Check for errors in
```scala mdoc:silent
val errors: Seq[((Module, String), Seq[String])] =
  resolution.errors
```
Any error would mean that the resolution wasn't able to get metadata about some dependencies.

Then fetch and get local copies of the artifacts themselves (the JARs) with
```scala mdoc:silent
import java.io.File
import coursier.cache.ArtifactError
import coursier.util.{Gather, Task}

val localArtifacts: Seq[Either[ArtifactError, File]] =
  Gather[Task].gather(
    resolution
      .artifacts()
      .map(Cache.default.file(_).run)
  ).unsafeRun()
```

See [the dedicated page](api.md) for more details.

