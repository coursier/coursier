package coursierbuild.modules

import coursierbuild.Deps.Deps
import mill._
import com.github.lolgab.mill.mima._

trait CacheJvmBase extends Cache with CsMima {
  def customLoaderCp: T[Seq[PathRef]]

  def mimaBinaryIssueFilters =
    super.mimaBinaryIssueFilters() ++ Seq(
      ProblemFilter.exclude[IncompatibleResultTypeProblem](
        "coursier.cache.PlatformCacheCompanion.default"
      ),
      ProblemFilter.exclude[IncompatibleResultTypeProblem]("coursier.cache.Cache.default"),
      // moved a different module (archive-cache, NOT pulled transitively)
      ProblemFilter.exclude[MissingClassProblem]("coursier.cache.ArchiveCache"),
      ProblemFilter.exclude[MissingClassProblem]("coursier.cache.ArchiveCache$"),
      ProblemFilter.exclude[MissingClassProblem]("coursier.cache.ArchiveType"),
      ProblemFilter.exclude[MissingClassProblem]("coursier.cache.ArchiveType$*"),
      ProblemFilter.exclude[MissingClassProblem]("coursier.cache.UnArchiver"),
      ProblemFilter.exclude[MissingClassProblem]("coursier.cache.UnArchiver$*"),
      // moved a different module (pulled transitively)
      ProblemFilter.exclude[MissingClassProblem]("coursier.paths.*"),
      // added methods on a sealed abstract class
      ProblemFilter.exclude[ReversedMissingMethodProblem]("coursier.cache.loggers.RefreshInfo.*"),
      // moved to cache-util module
      ProblemFilter.exclude[MissingClassProblem]("coursier.cache.internal.SigWinch"),
      // removed private class
      ProblemFilter.exclude[MissingClassProblem]("coursier.cache.internal.TmpConfig$AsJson"),
      ProblemFilter.exclude[MissingClassProblem]("coursier.cache.internal.TmpConfig$AsJson$"),
      // new methods added to sealed trait
      ProblemFilter.exclude[ReversedMissingMethodProblem](
        "coursier.cache.CachePolicy.acceptChanging"
      ),
      ProblemFilter.exclude[ReversedMissingMethodProblem](
        "coursier.cache.CachePolicy.rejectChanging"
      ),
      ProblemFilter.exclude[ReversedMissingMethodProblem](
        "coursier.cache.CachePolicy.acceptsChangingArtifacts"
      ),
      // private class
      ProblemFilter.exclude[Problem]("coursier.cache.CacheUrl#Args*"),
      ProblemFilter.exclude[Problem]("coursier.cache.CacheUrl$Args*"),
      ProblemFilter.exclude[Problem]("coursier.cache.CacheUrl.BasicRealm*"),
      // ignore shaded-stuff related errors
      ProblemFilter.exclude[Problem]("coursier.cache.shaded.*")
    )

  trait CacheJvmBaseTests extends CrossSbtTests {
    // Mill >= 1.1.6 reports classpath paths relative to the per-daemon sandbox
    // (`out/mill-daemon/sandbox/../mill-{workspace,home}/...`). Those only resolve while
    // that exact daemon session/sandbox layout is alive, which isn't guaranteed in the
    // forked test JVM (and breaks in CI). getCanonicalFile gives a stable, machine-absolute
    // path (resolving symlinks) without requiring the entry to exist on disk (some, e.g.
    // `compile-resources`, may not).
    def sources = Task {
      val dest = Task.dest / "CustomLoaderClasspath.scala"
      val customLoaderCp0 = customLoaderCp()
        .map(ref => "\"" + ref.path.toNIO.toFile.getCanonicalFile.toURI.toASCIIString + "\"")
        .mkString("Seq(", ", ", ")")
      val content =
        s"""package coursier.cache
           |object CustomLoaderClasspath {
           |  val files = $customLoaderCp0
           |}
           |""".stripMargin
      os.write(dest, content)
      super.sources() ++ Seq(PathRef(dest))
    }
  }
}
