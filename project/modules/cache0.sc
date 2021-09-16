import $file.^.deps, deps.Deps
import $file.^.shading, shading.Shading
import $file.shared, shared.{CoursierPublishModule, CsCrossJvmJsModule, CsMima, CsModule}

trait Cache extends CsModule with CsCrossJvmJsModule with CoursierPublishModule {
  def artifactName = "coursier-cache"
  def compileIvyDeps = Agg(
    Deps.dataClass
  )
}
trait CacheJvmBase extends Cache with CsMima with Shading {
  def customLoaderCp: T[Seq[PathRef]]

  def shadedDependencies = Agg.empty[mill.scalalib.Dep]
  def validNamespaces = Seq(
    "coursier.cache",
    "coursier.paths",
    "coursier.util"
  )
  def shadeRenames = Seq(
    "dev.dirs.**" -> "coursier.cache.shaded.dirs.@1"
  )

  def mimaBinaryIssueFilters = {
    import com.typesafe.tools.mima.core._
    super.mimaBinaryIssueFilters ++ Seq(
      // new methods added to sealed trait
      ProblemFilters.exclude[ReversedMissingMethodProblem]("coursier.cache.CachePolicy.acceptChanging"),
      ProblemFilters.exclude[ReversedMissingMethodProblem]("coursier.cache.CachePolicy.rejectChanging"),
      ProblemFilters.exclude[ReversedMissingMethodProblem]("coursier.cache.CachePolicy.acceptsChangingArtifacts"),
      // private class
      (pb: Problem) => pb.matchName.forall(!_.startsWith("coursier.cache.CacheUrl#Args")),
      (pb: Problem) => pb.matchName.forall(!_.startsWith("coursier.cache.CacheUrl$Args")),
      (pb: Problem) => pb.matchName.forall(!_.startsWith("coursier.cache.CacheUrl.BasicRealm")),
      // ignore shaded-stuff related errors
      (pb: Problem) => pb.matchName.forall(!_.startsWith("coursier.cache.shaded."))
    )
  }

  trait Tests extends super.Tests {
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
