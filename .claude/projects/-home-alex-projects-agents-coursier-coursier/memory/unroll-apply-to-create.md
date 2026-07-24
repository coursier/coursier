---
name: unroll-apply-to-create
description: Why coursier unroll branch renames convenience `apply` to `create`, and the CI-fix pattern
metadata:
  type: project
---

On the coursier `unroll` branch, companion-object convenience constructors were renamed
`def apply(...)` -> `def create(...)` (17 main-source files; ~47 main call sites already use
`.create`). This rename is NOT cosmetic — it is FORCED: on the Scala 3 unroll target (3.8.2),
a `@data case class` cannot also carry manual `apply` overloads. data-class's unroll macro then
fails with `wrong number of arguments at posttyper for (...): Authentication` (verified by
experiment on `Authentication`). So `@data` must stay AND convenience `apply` must be `create`.

CI-fix pattern for the resulting breakage (dominant root cause across the 2.12/2.13/native/website/
cli-tests jobs):
1. `FileCache` and `RemoteCache` MUST stay plain `case class` (NOT `@data`): they implement the
   abstract `Cache.WithLogger[F, Repr].withLogger` setter, and on Scala 3 `@data` generates NO `withX`
   setters, so `@data` there leaves the class abstract ("class FileCache needs to be abstract, since
   def withLogger … is not defined"). Restoring `@data` instead breaks the 3.8.2 main compile. The fix
   is the opposite: keep them plain and ADD the missing `withX` setters MANUALLY (the author had
   `withLogger`/`withLocation`/`withTtl`/… but was missing `withFollowHttpToHttpsRedirections`,
   `withChecksums`, `withCachePolicies`, `withPool`, `withRetry`, `withClassLoaders`, … on FileCache
   and `withPool`/`withCachePolicies`/`withWatchLenPool`/`withFileFallback` on RemoteCache). Provide
   BOTH the field-typed overload and the convenience one where tests use both, e.g. `withTtl(Duration)`
   AND `withTtl(Option[Duration])`, `withMaxRedirections(Int)` AND `(Option[Int])`, `withLocation(String)`
   AND `withLocation(File)`. `RefreshInfo`, `SbtMavenRepository`, `MavenRepository` are also
   intentionally plain (MavenRepository has an explanatory NOTE) — leave them.
2. Migrate remaining callers (test suites, `it`, cli-tests, docs `.md` snippets) from `X(...)` to
   `X.create(...)` for every convenience constructor that no longer matches the generated apply:
   `Resolve()` -> `Resolve.create()`, `MavenRepository(root)` -> `.create`, `Authentication(user)`
   -> `.create`, `Dependency(mod, ver)` -> `.create`, etc. The compiler is the arbiter (error:
   "overloaded method apply with alternatives" / "None of the overloaded alternatives"). Withers
   (`.withX`) are unaffected — they come from `@data`.

See [[coursier-unroll-build-task]] and [[unroll-build-mill-test-cross]].
