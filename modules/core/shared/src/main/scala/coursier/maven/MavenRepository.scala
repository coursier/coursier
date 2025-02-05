package coursier.maven

import coursier.core._
import coursier.util.{Artifact, EitherT, Monad}
import coursier.version.{Version => Version0, VersionConstraint => VersionConstraint0}
import dataclass._

object MavenRepository {

  def defaultConfigurations = MavenRepositoryInternal.defaultConfigurations

  private[coursier] def parseRawPomSax(str: String): Either[String, Project] =
    coursier.core.compatibility.xmlParseSax(str, new PomParser)
      .project

  private def actualRoot(root: String): String =
    root.stripSuffix("/")

  def apply(root: String): MavenRepository =
    new MavenRepository(actualRoot(root))
  def apply(root: String, authentication: Option[Authentication]): MavenRepository =
    new MavenRepository(
      actualRoot(root),
      authentication,
      changing = None,
      versionsCheckHasModule = true,
      checkModule = false
    )
}

@data(apply = false) class MavenRepository(
  root: String,
  authentication: Option[Authentication] = None,
  @since
  changing: Option[Boolean] = None,
  @since
  override val versionsCheckHasModule: Boolean = true,
  @since("2.1.25")
  override val checkModule: Boolean = false
) extends MavenRepositoryLike.WithModuleSupport with Repository.VersionApi {

  private val internal = new MavenRepositoryInternal(
    root,
    authentication,
    changing,
    checkModule
  )

  def artifacts(
    dependency: Dependency,
    project: Project,
    overrideClassifiers: Option[Seq[Classifier]]
  ): Seq[(Publication, Artifact)] =
    internal.artifacts(dependency, project, overrideClassifiers)

  def moduleArtifacts(
    dependency: Dependency,
    project: Project
  ): Seq[(VariantPublication, Artifact)] =
    internal.moduleArtifacts(dependency, project)

  override def find0[F[_]](
    module: Module,
    version: Version0,
    fetch: Repository.Fetch[F]
  )(implicit
    F: Monad[F]
  ): EitherT[F, String, (ArtifactSource, Project)] =
    internal.find(module, version, fetch).map((this, _))

  def moduleDirectory(module: Module): String =
    internal.moduleDirectory(module)

  def urlFor(path: Seq[String], isDir: Boolean = false): String =
    internal.urlFor(path, isDir)

  def artifactFor(url: String, changing: Boolean): Artifact =
    internal.artifactFor(url, changing)

  def withChanging(changing: Boolean): MavenRepository =
    withChanging(Some(changing))

  override def fetchVersions[F[_]](
    module: Module,
    fetch: Repository.Fetch[F]
  )(implicit
    F: Monad[F]
  ): EitherT[F, String, (Versions, String)] =
    internal.fetchVersions(module, fetch)

  override def completeOpt[F[_]: Monad](fetch: Repository.Fetch[F]): Some[Repository.Complete[F]] =
    Some(MavenComplete(this, fetch, Monad[F]))
}
