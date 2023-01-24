package coursier.maven

import coursier.core._
import coursier.core.compatibility.encodeURIComponent
import coursier.util.{Artifact, EitherT, Monad, WebPage}
import coursier.util.Monad.ops._
import dataclass._

import scala.collection.compat._

object SbtMavenRepository {
  private def actualRoot(root: String): String =
    root.stripSuffix("/")

  def apply(root: String): SbtMavenRepository =
    new SbtMavenRepository(actualRoot(root))
  def apply(root: String, authentication: Option[Authentication]): SbtMavenRepository =
    new SbtMavenRepository(actualRoot(root), authentication = authentication, None, true)

  def apply(repo: MavenRepository): SbtMavenRepository =
    new SbtMavenRepository(
      repo.root,
      repo.authentication,
      repo.changing,
      repo.versionsCheckHasModule
    )
}

@data(apply = false) class SbtMavenRepository(
  override val root: String,
  override val authentication: Option[Authentication] = None,
  override val changing: Option[Boolean] = None,
  override val versionsCheckHasModule: Boolean = true
) extends MavenRepositoryBase {
  import SbtMavenRepository._

  def withChanging(changing: Boolean): SbtMavenRepository =
    withChanging(Some(changing))

  private[coursier] override def moduleDirectory(module: Module): String =
    SbtPom.getSbtCrossVersion(module.attributes)
      .fold(module.name.value)(crossVersion => module.name.value + crossVersion)

  override protected def rawPomParser: Pom    = SbtPom
  override protected def saxParser: PomParser = new SbtPomParser

  override protected def fetchArtifact[F[_]](
    module: Module,
    version: String,
    versioningValue: Option[String],
    fetch: Repository.Fetch[F]
  )(implicit F: Monad[F]): EitherT[F, String, Project] = {
    val directoryPath = moduleVersionPath(module, version)

    def tryFetch(artifactName: String): EitherT[F, String, Project] = {
      val path     = directoryPath :+ s"$artifactName-${versioningValue.getOrElse(version)}.pom"
      val artifact = projectArtifact(path, version)
      fetch(artifact).flatMap(parsePom(_))
    }

    SbtPom.getSbtCrossVersion(module.attributes) match {
      case Some(crossVersion) =>
        /** In case of an sbt plugin, for instance org.example:example:1.0.0 with extra-attributes
          * scalaVersion->2.12 and sbtVersion->1.0, we first try the valid Maven pattern
          * 'org/example/example_2.12_1.0/1.0.0/example_2.12_1.0-1.0.0-jar' then the legacy pattern
          * 'org/example/example_2.12_1.0/1.0.0/example-1.0.0-jar`
          */
        tryFetch(module.name.value + crossVersion).orElse(tryFetch(module.name.value))
      case None => tryFetch(module.name.value)
    }
  }

  override protected def tryListVersions(module: Module): Boolean =
    changing.forall(!_) && SbtPom.getSbtCrossVersion(module.attributes).isDefined
}
