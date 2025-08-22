package coursierbuild.modules

import coursierbuild.Deps.Deps
import coursierbuild.Shading
import com.github.lolgab.mill.mima._

import mill._, mill.scalalib._

trait CoursierJvmBase extends Coursier with CsMima with Shading {

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

  def shadedDependencies = Agg(
    Deps.fastParse,
    Deps.pprint
  )
  def validNamespaces = Seq("coursier")
  def shadeRenames    = Seq(
    "fastparse.**"  -> "coursier.internal.shaded.fastparse.@1",
    "geny.**"       -> "coursier.internal.shaded.geny.@1",
    "sourcecode.**" -> "coursier.internal.shaded.sourcecode.@1",
    "pprint.**"     -> "coursier.internal.shaded.pprint.@1"
  )
}
