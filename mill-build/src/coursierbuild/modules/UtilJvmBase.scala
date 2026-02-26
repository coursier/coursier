package coursierbuild.modules

import com.github.lolgab.mill.mima.*
import coursierbuild.Shading
import coursierbuild.Deps.Deps
import mill.*

trait UtilJvmBase extends Util with CsMima with Shading {

  def manifest = super[Shading].manifest

  def mimaBinaryIssueFilters =
    super.mimaBinaryIssueFilters() ++
      Seq(
        ProblemFilter.exclude[Problem]("coursier.util.shaded.*")
      )
  def shadedDependencies = Seq(
    Deps.jsoup
  )
  def validNamespaces = Seq("coursier")
  def shadeRenames = Seq(
    "org.jsoup.**" -> "coursier.util.shaded.org.jsoup.@1"
  )
}
