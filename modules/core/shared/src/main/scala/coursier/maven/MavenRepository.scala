package coursier.maven

import coursier.core._
import coursier.util.{Artifact, EitherT, Monad}
import dataclass._

object MavenRepository {

  val defaultConfigurations = Map(
    Configuration.compile -> Seq.empty,
    Configuration.runtime -> Seq(Configuration.compile),
    Configuration.default -> Seq(Configuration.runtime),
    Configuration.test    -> Seq(Configuration.runtime)
  )

  private[coursier] def parseRawPomSax(str: String): Either[String, Project] =
    coursier.core.compatibility.xmlParseSax(str, new PomParser)
      .project

  private def actualRoot(root: String): String =
    root.stripSuffix("/")

  def apply(root: String): MavenRepository =
    new MavenRepository(actualRoot(root))
  def apply(root: String, authentication: Option[Authentication]): MavenRepository =
    new MavenRepository(actualRoot(root), authentication)
}

@data(apply = false) class MavenRepository(
  root: String,
  authentication: Option[Authentication] = None,
  @since
  changing: Option[Boolean] = None,
  @since
  override val versionsCheckHasModule: Boolean = true
) extends MavenRepositoryLike {

  private val internal = new MavenRepositoryInternal(root, authentication, changing)

  def artifacts(
    dependency: Dependency,
    project: Project,
    overrideClassifiers: Option[Seq[Classifier]]
  ): Seq[(Publication, Artifact)] =
    internal.artifacts(dependency, project, overrideClassifiers)

  def find[F[_]](
    module: Module,
    version: String,
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
