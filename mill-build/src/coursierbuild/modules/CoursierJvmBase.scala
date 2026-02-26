package coursierbuild.modules

import com.github.lolgab.mill.mima.*
import coursierbuild.Shading
import coursierbuild.Deps.Deps
import mill.*
import mill.scalalib.*

trait CoursierJvmBase extends Coursier with CsMima with Shading {

  def manifest = super[Shading].manifest

  def mimaBinaryIssueFilters =
    super.mimaBinaryIssueFilters() ++ Seq(
      // added new abstract method to sealed class, should be safe
      ProblemFilter.exclude[ReversedMissingMethodProblem]("coursier.parse.JavaOrScalaDependency.*"),
      // changed private[coursier] method
      ProblemFilter.exclude[DirectMissingMethodProblem]("coursier.Resolve.initialResolution"),
      // removed private[coursier] method
      ProblemFilter.exclude[DirectMissingMethodProblem]("coursier.Artifacts.artifacts0"),
      // ignore shaded-stuff related errors
      ProblemFilter.exclude[Problem]("coursier.internal.shaded.*")
    )

  def shadedDependencies = Seq(
    Deps.fastParse,
    Deps.pprint
  )
  def validNamespaces = Seq("coursier")
  def shadeRenames = Seq(
    "fastparse.**"  -> "coursier.internal.shaded.fastparse.@1",
    "geny.**"       -> "coursier.internal.shaded.geny.@1",
    "sourcecode.**" -> "coursier.internal.shaded.sourcecode.@1",
    "pprint.**"     -> "coursier.internal.shaded.pprint.@1"
  )
}
