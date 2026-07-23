---
name: dataclass-unroll-compat
description: Constraints of the data-class 0.2.8 @unroll compatibility mode used on the coursier `unroll` branch
metadata:
  type: project
---

On the coursier `unroll` branch we migrate data classes to data-class 0.2.8's `@unroll` compat mode so ONE source cross-compiles on Scala 2.12/2.13 and Scala 3.

Pattern: `import dataclass.{data, since => unroll}` + `@data case class Foo(a: Int, @unroll b: Boolean = false)`. On Scala 2 the `@data` macro expands (needs `-Ymacro-annotations` on 2.13, macro-paradise plugin on 2.12); on Scala 3 `@data` is a no-op and `since` aliases `scala.annotation.unroll` (needs `-experimental`).

Hard constraints found:
- data-class 0.2.8 is built for **Scala 3.8.2** (pulls `scala-library:3.8.2`); the Scala 3 build MUST use 3.8.2+ or TASTy is forward-incompatible. `scala3Unroll = "3.8.2"`.
- Scala 3.8.x only accepts `-java-output-version` **17+** (8/9/11/16 rejected). CsScalaModule bumps release to 17 on Scala 3.
- On Scala 3 the `data` annotation takes NO params, so compat mode requires plain `@data` — drop options like `@data(apply = false, cachedHashCode = true)`.
- data-class 0.2.8 generates `apply`/`copy`/`withX`; a manual one with the SAME signature ⇒ "defined twice" on Scala 2. It defers to user-defined `withX` for all fields EXCEPT the LAST `@unroll` field (still generated ⇒ conflict).
- On Scala 3, `@data case class` generates NO `withX` setters (case class only has `copy`). So classes needing `withX` (esp. implementing abstract trait setters like `MavenRepositoryLike.WithModuleSupport.withCheckModule`) can't win both sides — dropped `@data` there (kept `@unroll`), so Scala 3 unrolls natively and Scala 2 is a plain case class.
- Module/Publication had a custom caching companion `apply` (+ manual copy) incompatible with case-class apply — dropped the interning to compile.
- Scala 2.12: `val x = f(y.copy(named = ...))` can need an explicit result type (2.12 named-arg-vs-assignment ambiguity with overloaded copy).
- Scala 3 is stricter: `@tailrec` methods must be `final`/`private`.

See [[[coursier-unroll-build-task]]] for the overall task.
