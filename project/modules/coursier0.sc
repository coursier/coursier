import $file.^.deps, deps.Deps
import $file.^.shading, shading.Shading
import $file.shared, shared.{CoursierPublishModule, CsCrossJvmJsModule, CsMima, CsModule}
import com.github.lolgab.mill.mima._

import mill._, mill.scalalib._

trait Coursier extends CsModule with CsCrossJvmJsModule with CoursierPublishModule {
  def artifactName = "coursier"
  def compileIvyDeps = super.compileIvyDeps() ++ Agg(
    Deps.dataClass,
    Deps.jsoniterMacros,
    Deps.scalaReflect(scalaVersion()) // ???
  )
  def ivyDeps = super.ivyDeps() ++ Agg(
    Deps.dependency,
    Deps.fastParse,
    Deps.jsoniterCore
  )
}
trait CoursierTests extends TestModule {
  def ivyDeps = T {
    super.ivyDeps() ++ Agg(
      Deps.diffUtils,
      Deps.pprint,
      Deps.scalaAsync
    )
  }
}
trait CoursierJvmBase extends Coursier with CsMima with Shading {

  def mimaBinaryIssueFilters =
    super.mimaBinaryIssueFilters() ++ Seq(
      // changed private[coursier] method
      ProblemFilter.exclude[DirectMissingMethodProblem]("coursier.Resolve.initialResolution"),
      // removed private[coursier] method
      ProblemFilter.exclude[DirectMissingMethodProblem]("coursier.Artifacts.artifacts0"),
      // ignore shaded-stuff related errors
      ProblemFilter.exclude[Problem]("coursier.internal.shaded.*")
    )

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
