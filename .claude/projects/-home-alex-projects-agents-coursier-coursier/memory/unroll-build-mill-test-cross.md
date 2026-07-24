---
name: unroll-build-mill-test-cross
description: How the coursier unroll branch keeps nested test modules out of the scala3Unroll cross
metadata:
  type: project
---

On the coursier `unroll` branch, main modules cross-build on `ScalaVersions.allWithUnroll`
(2.12/2.13 + 3.8.2). The 3.8.2 value only exists to check the data-class MAIN sources compile via
`@unroll`. Nested `object test`/`object it` modules inherit that cross value, but they can't build at
3.8.2: their sources are un-migrated Scala 2 test code and they depend on Scala-2-only support
modules (`test-cache`, `cache-server`). Symptom: `./mill __.js.__.compile` fails with
`Unable to find compatible cross version between 3.8.2 and 2.13.18,2.12.20`.

Fix in build.mill: a top-level `def isUnrollOnly(crossValue: String) = crossValue == ScalaVersions.scala3Unroll`,
then in each affected nested test/it module (core.jvm/js, cache.jvm, archive-cache, launcher, env,
coursier.jvm test+it, coursier.js) blank the sources and drop the Scala-2-only deps for that value:
`def sources = if (isUnrollOnly(crossValue)) Task.Sources() else <orig>` and
`def moduleDeps = super.moduleDeps ++ (if (isUnrollOnly(crossValue)) Nil else Seq(...))`.
Also: an empty test module still resolves `mvnDeps`, and `scala-async` has no Scala 3 artifact — guard
it with `if (scalaVersion().startsWith("2.")) Seq(Deps.scalaAsync) else Nil` (done in `CoursierTests`,
which had to become `extends ScalaModule` to see `scalaVersion`, plus cache/archive-cache test blocks).
Verified: `./mill __[3.8.2].compile` gets past all test modules. See [[unroll-apply-to-create]].
