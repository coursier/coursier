package coursier.maven

import coursier.core._
import coursier.core.compatibility.encodeURIComponent
import coursier.util.{Artifact, EitherT, Monad, WebPage}
import coursier.util.Monad.ops._
import dataclass._

import scala.collection.compat._

object MavenRepository {

  val defaultConfigurations =
    MavenRepositoryBase.defaultConfigurations

  private def actualRoot(root: String): String =
    root.stripSuffix("/")

  def apply(root: String): MavenRepository =
    new MavenRepository(actualRoot(root))
  def apply(root: String, authentication: Option[Authentication]): MavenRepository =
    new MavenRepository(actualRoot(root), authentication)
}
@data(apply = false) class MavenRepository(
  override val root: String,
  override val authentication: Option[Authentication] = None,
  @since
  override val changing: Option[Boolean] = None,
  @since
  override val versionsCheckHasModule: Boolean = true
) extends MavenRepositoryBase {

  def withChanging(changing: Boolean): MavenRepository =
    withChanging(Some(changing))

  private[coursier] override def moduleDirectory(module: Module): String =
    module.name.value

  override protected def rawPomParser: Pom    = Pom
  override protected def saxParser: PomParser = new PomParser

  override protected def fetchArtifact[F[_]](
    module: Module,
    version: String,
    versioningValue: Option[String],
    fetch: Repository.Fetch[F]
  )(implicit F: Monad[F]): EitherT[F, String, Project] = {
    val directoryPath = moduleVersionPath(module, version)
    val moduleName    = module.name.value
    val path          = directoryPath :+ s"$moduleName-${versioningValue.getOrElse(version)}.pom"
    val artifact      = projectArtifact(path, version)
    fetch(artifact).flatMap(parsePom(_))
  }
}
