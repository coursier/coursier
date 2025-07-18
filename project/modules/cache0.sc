import $file.^.deps, deps.Deps
import $file.shared, shared.{CoursierPublishModule, CsCrossJvmJsModule, CsMima, CsModule}
import mill._
import com.github.lolgab.mill.mima._

trait Cache extends CsModule with CsCrossJvmJsModule with CoursierPublishModule {
  def artifactName = "coursier-cache"
  def compileIvyDeps = Agg(
    Deps.dataClass
  )
}
trait CacheJvmBase extends Cache with CsMima {
  def customLoaderCp: T[Seq[PathRef]]

  def mimaBinaryIssueFilters =
    super.mimaBinaryIssueFilters() ++ Seq(
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
    def sources = T.sources {
      val dest = T.dest / "CustomLoaderClasspath.scala"
      val customLoaderCp0 = customLoaderCp()
        .map("\"" + _.path.toNIO.toUri.toASCIIString + "\"")
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

trait ArchiveCacheBase extends CsModule with CsCrossJvmJsModule with CoursierPublishModule
    with CsMima {
  def artifactName = "coursier-archive-cache"
  def compileIvyDeps = Agg(
    Deps.dataClass
  )
}
