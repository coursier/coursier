import $file.^.deps, deps.Deps
import $file.^.shading, shading.Shading
import $file.shared, shared.{CoursierPublishModule, CsCrossJvmJsModule, CsMima, CsModule}

import mill._, mill.scalalib._

trait Coursier extends CsModule with CsCrossJvmJsModule with CoursierPublishModule {
  def artifactName = "coursier"
  def compileIvyDeps = super.compileIvyDeps() ++ Agg(
    Deps.dataClass,
    Deps.jsoniterMacros,
    Deps.scalaReflect(scalaVersion()) // ???
  )
  def ivyDeps = super.ivyDeps() ++ Agg(
    Deps.fastParse,
    Deps.jsoniterCore,
    Deps.scalaCliConfig
  )
}
trait CoursierTests extends TestModule {
  def ivyDeps = T {
    super.ivyDeps() ++ Agg(
      Deps.scalaAsync
    )
  }
}
trait CoursierJvmBase extends Coursier with CsMima with Shading {

  def mimaBinaryIssueFilters = {
    import com.typesafe.tools.mima.core._
    super.mimaBinaryIssueFilters ++ Seq(
      // changed private[coursier] method
      ProblemFilters.exclude[DirectMissingMethodProblem]("coursier.Resolve.initialResolution"),
      // removed private[coursier] method
      ProblemFilters.exclude[DirectMissingMethodProblem]("coursier.Artifacts.artifacts0"),
      // ignore shaded-stuff related errors
      (pb: Problem) => pb.matchName.forall(!_.startsWith("coursier.core.shaded."))
    )
  }

  def shadedDependencies = Agg(
    Deps.fastParse
  )
  def validNamespaces = Seq("coursier")
  def shadeRenames = Seq(
    "fastparse.**"  -> "coursier.internal.shaded.fastparse.@1",
    "geny.**"       -> "coursier.internal.shaded.geny.@1",
    "sourcecode.**" -> "coursier.internal.shaded.sourcecode.@1"
  )
}
