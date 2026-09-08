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
      // internal, in spite of what their compiled visibility says
      ProblemFilter.exclude[Problem]("coursier.cache.internal.Retry*"),
      ProblemFilter.exclude[Problem]("coursier.cache.internal.Downloader#Blocking*"),
      ProblemFilter.exclude[Problem]("coursier.cache.internal.Downloader$Blocking*"),
      // ignore shaded-stuff related errors
      ProblemFilter.exclude[Problem]("coursier.cache.shaded.*")
    )

  trait CacheJvmBaseTests extends CrossSbtTests {
    // These URIs are baked into the generated source below, so they outlive the run that
    // computed them. PathRef.toAbsFile only de-aliases lexically, which leaves them pointing
    // through Mill's `mill-{workspace,home}` forwarder symlinks - those are session-scoped, and
    // a stale one makes the forked test JVM fail to load the custom protocol handler.
    // getCanonicalFile resolves them, and unlike toRealPath it copes with entries that don't
    // exist on disk (e.g. `compile-resources`).
    def sources = Task {
      val dest = Task.dest / "CustomLoaderClasspath.scala"
      val customLoaderCp0 = customLoaderCp()
        .map { ref =>
          val file = PathRef.toAbsFile(ref).getCanonicalFile
          val uri  = file.toURI.toASCIIString
          val normalized =
            if (file.isDirectory && !uri.endsWith("/")) uri + "/"
            else uri
          "\"" + normalized + "\""
        }
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
