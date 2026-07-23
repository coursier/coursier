---
name: coursier-unroll-build-task
description: Goal + approach for reworking the coursier `unroll` branch onto data-class 0.2.8 compat mode
metadata:
  type: project
---

Task (2026-07): on the coursier `unroll` branch, rework the 3 commits on top of `main` so that:
1. Scala 2.12 support is restored (revert "Remove Scala 2.12 support"), and build.mill is back to `main`'s shape (undo the Scala-3-only pinning from the other two commits).
2. data-class bumped 0.2.7 -> 0.2.8, using its `@unroll` compatibility mode so the SAME source compiles on Scala 2.12, 2.13 AND a Scala 3 version (3.8.2, forced by data-class 0.2.8's TASTy).

Approach: keep the unroll-converted sources but rewrite each migrated class as `@data case class` with `import dataclass.{data, since => unroll}` (script did import swap + `@data` prefix). build.mill: `ScalaVersions.allWithUnroll = all :+ scala3Unroll`; data-class restored as a compileMvnDep on the core module traits; per-module crosses opt into `allWithUnroll`.

Status: util + core fully cross-compile on 2.12/2.13/3.8.2. See [[dataclass-unroll-compat]] for the per-class gotchas hit along the way.
