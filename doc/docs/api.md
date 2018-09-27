---
title: API
---

Add to your `build.sbt`
```scala
libraryDependencies ++= Seq(
  "io.get-coursier" %% "coursier" % "1.0.3",
  "io.get-coursier" %% "coursier-cache" % "1.0.3"
)
```

Note that the examples below are validated against the current sources of coursier. You may want to read the [documentation of the latest release](https://github.com/coursier/coursier/blob/v1.1.0-M7/README.md#api) of coursier instead.

Add an import for coursier,
```scala mdoc:silent
import coursier._
```

```scala mdoc:passthrough
import coursier.{ Cache => _, _ }
```

```scala mdoc:passthrough
object Cache {
  val ivy2LocalIsIvy = coursier.Cache.ivy2Local match {
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

  def fetch[F[_]: coursier.util.Schedulable]() = coursier.Cache.fetch[F]()
  def file[F[_]: coursier.util.Schedulable](artifact: Artifact) = coursier.Cache.file[F](artifact)
}
```

To resolve dependencies, first create a `Resolution` case class with your dependencies in it,
```scala mdoc:silent
val start = Resolution(
  Set(
    Dependency(
      Module("org.scalaz", "scalaz-core_2.11"), "7.2.3"
    ),
    Dependency(
      Module("org.typelevel", "cats-core_2.11"), "0.6.0"
    )
  )
)
```

Create a fetch function able to get things from a few repositories via a local cache,
```scala mdoc:silent
import coursier.util.Task

val repositories = Seq(
  Cache.ivy2Local,
  MavenRepository("https://repo1.maven.org/maven2")
)

val fetch = Fetch.from(repositories, Cache.fetch[Task]())
```

Then run the resolution per-se,
```scala mdoc:silent
import scala.concurrent.ExecutionContext.Implicits.global

val resolution = start.process.run(fetch).unsafeRun()
```
That will fetch and use metadata.

Check for errors in
```scala mdoc:silent
val errors: Seq[((Module, String), Seq[String])] = resolution.errors
```
These would mean that the resolution wasn't able to get metadata about some dependencies.

Then fetch and get local copies of the artifacts themselves (the JARs) with
```scala mdoc:silent
import java.io.File
import coursier.util.Gather

val localArtifacts: Seq[Either[FileError, File]] = Gather[Task].gather(
  resolution.artifacts.map(Cache.file[Task](_).run)
).unsafeRun()
```


The default global cache used by coursier is `~/.coursier/cache/v1`. E.g. the artifact at
`https://repo1.maven.org/maven2/org/scala-lang/scala-library/2.11.8/scala-library-2.11.8.jar`
will land in `~/.coursier/cache/v1/https/repo1.maven.org/maven2/org/scala-lang/scala-library/2.11.8/scala-library-2.11.8.jar`.

From the SBT plugin, the default repositories are the ones provided by SBT (typically Central or JFrog, and `~/.ivy2/local`).
From the CLI tools, these are Central (`https://repo1.maven.org/maven2`) and `~/.ivy2/local`.
From the API, these are specified manually - you are encouraged to use those too.

