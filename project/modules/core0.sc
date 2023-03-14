import $file.^.deps, deps.Deps
import $file.^.shading, shading.Shading
import $file.shared,
  shared.{CoursierPublishModule, CsCrossJvmJsModule, CsMima, CsModule, commitHash}

trait Core extends CsModule with CsCrossJvmJsModule with CoursierPublishModule {
  def artifactName = "coursier-core"
  def compileIvyDeps = super.compileIvyDeps() ++ Agg(
    Deps.dataClass
  )
  def ivyDeps = super.ivyDeps() ++ Agg(
    Deps.fastParse
  )

  def constantsFile = T {
    val dest = T.dest / "Properties.scala"
    val code =
      s"""package coursier.util
         |
         |/** Build-time constants. Generated from mill. */
         |object Properties {
         |  def version = "${publishVersion()}"
         |  def commitHash = "${commitHash()}"
         |}
         |""".stripMargin
    os.write(dest, code)
    PathRef(dest)
  }
  def generatedSources = super.generatedSources() ++ Seq(constantsFile())
}
trait CoreJvmBase extends Core with CsMima with Shading {

  def mimaBinaryIssueFilters = {
    import com.typesafe.tools.mima.core._
    super.mimaBinaryIssueFilters ++ Seq(
      // false positives, coursier.core.DependencySet#Sets is private
      ProblemFilters.exclude[IncompatibleMethTypeProblem]("coursier.core.DependencySet#Sets.copy"),
      ProblemFilters.exclude[IncompatibleResultTypeProblem](
        "coursier.core.DependencySet#Sets.copy$default$1"
      ),
      ProblemFilters.exclude[IncompatibleMethTypeProblem]("coursier.core.DependencySet#Sets.this"),
      ProblemFilters.exclude[IncompatibleMethTypeProblem]("coursier.core.DependencySet#Sets.apply"),
      ProblemFilters.exclude[IncompatibleResultTypeProblem](
        "coursier.core.DependencySet#Sets.required"
      ),

      // PomParser#State is private, so this can be ignored
      ProblemFilters.exclude[DirectMissingMethodProblem]("coursier.maven.PomParser#State.licenses"),

      // ignore shaded-stuff related errors
      (pb: Problem) => pb.matchName.forall(!_.startsWith("coursier.core.shaded."))
    )
  }

  def shadedDependencies = Agg(
    Deps.fastParse
  )
  def validNamespaces = Seq("coursier")
  def shadeRenames = Seq(
    "fastparse.**"  -> "coursier.core.shaded.fastparse.@1",
    "geny.**"       -> "coursier.core.shaded.geny.@1",
    "sourcecode.**" -> "coursier.core.shaded.sourcecode.@1"
  )
}
