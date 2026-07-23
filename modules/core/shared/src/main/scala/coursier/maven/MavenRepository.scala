package coursier.maven

// NOTE: this class keeps `@unroll` for native Scala 3 unrolling but intentionally omits `@data`:
// its `withCheckModule` etc. implement abstract members of `MavenRepositoryLike.WithModuleSupport`,
// which clash with the setters data-class would generate for the last `@unroll` field on Scala 2.
import dataclass.{since => unroll}

import coursier.core._
import coursier.util.{Artifact, EitherT, Monad}
import coursier.version.{Version => Version0}
object MavenRepository {

  def defaultConfigurations = MavenRepositoryInternal.defaultConfigurations

  private[coursier] def parseRawPomSax(str: String): Either[String, Project] =
    coursier.core.compatibility.xmlParseSax(str, new PomParser)
      .project

  private[coursier] def parseRawPomDom(str: String): Either[String, Project] =
    Pom.project(coursier.core.compatibility.xmlParseDom(str).toOption.get)

  private def actualRoot(root: String): String =
    root.stripSuffix("/")

  def create(root: String): MavenRepository =
    new MavenRepository(actualRoot(root))
  def create(root: String, authentication: Option[Authentication]): MavenRepository =
    new MavenRepository(
      actualRoot(root),
      authentication,
      changing = None,
      versionsCheckHasModule = true,
      checkModule = false
    )
}

case class MavenRepository(
  root: String,
  authentication: Option[Authentication] = None,
  @unroll
  changing: Option[Boolean] = None,
  @unroll
  override val versionsCheckHasModule: Boolean = true,
  @unroll
  override val checkModule: Boolean = false
) extends MavenRepositoryLike.WithModuleSupport with Repository.VersionApi {

  private[coursier] val internal = new MavenRepositoryInternal(
    root,
    authentication,
    changing,
    checkModule
  )

  def withRoot(root: String): MavenRepository =
    copy(root = root)
  def withAuthentication(authentication: Option[Authentication]): MavenRepository =
    copy(authentication = authentication)
  def withVersionsCheckHasModule(versionsCheckHasModule: Boolean): MavenRepository =
    copy(versionsCheckHasModule = versionsCheckHasModule)
  def withCheckModule(checkModule: Boolean): MavenRepository =
    copy(checkModule = checkModule)

  def artifacts(
    dependency: Dependency,
    project: Project,
    overrideClassifiers: Option[Seq[Classifier]]
  ): Seq[(Publication, Artifact)] =
    internal.artifacts(dependency, project, overrideClassifiers)

  def moduleArtifacts(
    dependency: Dependency,
    project: Project,
    overrideAttributes: Option[VariantSelector.AttributesBased]
  ): Seq[(VariantPublication, Artifact)] =
    internal.moduleArtifacts(dependency, project, overrideAttributes)

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
    copy(changing = Some(changing))

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
