import $file.^.deps, deps.Deps
import $file.^.shading, shading.Shading
import $file.shared, shared.{CoursierPublishModule, CsCrossJvmJsModule, CsMima, CsModule}
import mill._
import com.github.lolgab.mill.mima._

trait Core extends CsModule with CsCrossJvmJsModule with CoursierPublishModule {
  def artifactName = "coursier-core"
  def compileIvyDeps = super.compileIvyDeps() ++ Agg(
    Deps.dataClass,
    Deps.jsoniterMacros
  )
  def ivyDeps = super.ivyDeps() ++ Agg(
    Deps.fastParse,
    Deps.jsoniterCore,
    Deps.pprint,
    Deps.versions
  )

  def commitHash: T[String]

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

  def mimaBinaryIssueFilters =
    super.mimaBinaryIssueFilters() ++ Seq(
      // new abstract methods added on sealed class, ought to be fine
      ProblemFilter.exclude[ReversedMissingMethodProblem]("coursier.graph.DependencyTree.*"),
      ProblemFilter.exclude[ReversedMissingMethodProblem]("coursier.graph.ModuleTree.*"),
      ProblemFilter.exclude[ReversedMissingMethodProblem]("coursier.graph.ReverseModuleTree.*"),
      ProblemFilter.exclude[ReversedMissingMethodProblem]("coursier.core.Reconciliation.*"),

      // private case class
      ProblemFilter.exclude[Problem]("coursier.graph.ModuleTree#Node.*"),

      // added abstract method to sealed abstract class, should be safe
      ProblemFilter.exclude[ReversedMissingMethodProblem](
        "coursier.core.MinimizedExclusions#ExclusionData.hasProperties"
      ),

      // false positives, coursier.core.Resolution#DepMgmt is private
      ProblemFilter.exclude[MissingClassProblem]("coursier.core.Resolution$DepMgmt$"),
      ProblemFilter.exclude[Problem]("coursier.core.Resolution#DepMgmt.*"),

      // false positives, coursier.core.DependencySet#Sets is private
      ProblemFilter.exclude[IncompatibleMethTypeProblem]("coursier.core.DependencySet#Sets.copy"),
      ProblemFilter.exclude[IncompatibleResultTypeProblem](
        "coursier.core.DependencySet#Sets.copy$default$1"
      ),
      ProblemFilter.exclude[IncompatibleMethTypeProblem]("coursier.core.DependencySet#Sets.this"),
      ProblemFilter.exclude[IncompatibleMethTypeProblem]("coursier.core.DependencySet#Sets.apply"),
      ProblemFilter.exclude[IncompatibleResultTypeProblem](
        "coursier.core.DependencySet#Sets.required"
      ),

      // PomParser#State is private, so this can be ignored
      ProblemFilter.exclude[DirectMissingMethodProblem]("coursier.maven.PomParser#State.licenses"),

      // ignore shaded-stuff related errors
      ProblemFilter.exclude[Problem]("coursier.core.shaded.*")
    )

  def shadedDependencies = Agg(
    Deps.fastParse,
    Deps.jsoniterCore,
    Deps.pprint
  )
  def validNamespaces = Seq("coursier")
  def shadeRenames = Seq(
    "com.github.plokhotnyuk.jsoniter_scala.**" -> "coursier.core.shaded.jsoniter.@1",
    "fastparse.**"                             -> "coursier.core.shaded.fastparse.@1",
    "geny.**"                                  -> "coursier.core.shaded.geny.@1",
    "sourcecode.**"                            -> "coursier.core.shaded.sourcecode.@1",
    "pprint.**"                                -> "coursier.core.shaded.pprint.@1"
  )
}
