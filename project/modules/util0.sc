import $file.^.deps, deps.Deps
import $file.^.shading, shading.Shading
import $file.shared, shared.{CoursierPublishModule, CsCrossJvmJsModule, CsMima, CsModule}
import com.github.lolgab.mill.mima._

trait Util extends CsModule with CsCrossJvmJsModule with CoursierPublishModule {
  def artifactName = "coursier-util"
  def ivyDeps = Agg(
    Deps.collectionCompat
  )
  def compileIvyDeps = Agg(
    Deps.dataClass,
    Deps.simulacrum
  )
}

trait UtilJvmBase extends Util with CsMima with Shading {
  def mimaBinaryIssueFilters =
    super.mimaBinaryIssueFilters() ++
      Seq(
        ProblemFilter.exclude[Problem]("coursier.util.shaded.*")
      )
  def shadedDependencies = Agg(
    Deps.jsoup
  )
  def validNamespaces = Seq("coursier")
  def shadeRenames = Seq(
    "org.jsoup.**" -> "coursier.util.shaded.org.jsoup.@1"
  )
}
