package coursier.maven

import coursier.core._
import coursier.util.{Artifact, EitherT, Monad, Xml}
import coursier.version.{Version => Version0, VersionConstraint => VersionConstraint0}
import dataclass._

import scala.collection.compat._

object SbtMavenRepository {
  private def actualRoot(root: String): String =
    root.stripSuffix("/")

  def apply(root: String): SbtMavenRepository =
    new SbtMavenRepository(actualRoot(root))
  def apply(root: String, authentication: Option[Authentication]): SbtMavenRepository =
    new SbtMavenRepository(
      actualRoot(root),
      authentication = authentication,
      changing = None,
      versionsCheckHasModule = true,
      checkModule = false
    )

  def apply(repo: MavenRepository): SbtMavenRepository =
    new SbtMavenRepository(
      repo.root,
      repo.authentication,
      repo.changing,
      repo.versionsCheckHasModule,
      repo.checkModule
    )

  private def extraAttributes(s: String)
    : Either[String, Map[(Module, VersionConstraint0), Map[String, String]]] = {
    val lines = s.split('\n').toSeq.map(_.trim).filter(_.nonEmpty)

    lines.foldLeft[Either[String, Map[(Module, VersionConstraint0), Map[String, String]]]](
      Right(Map.empty)
    ) {
      case (acc, line) =>
        for {
          modVers <- acc
          modVer  <- extraAttribute(line)
        } yield modVers + modVer
    }
  }

  private def extraAttribute(s: String)
    : Either[String, ((Module, VersionConstraint0), Map[String, String])] = {
    // vaguely does the same as:
    // https://github.com/apache/ant-ivy/blob/2.2.0/src/java/org/apache/ivy/core/module/id/ModuleRevisionId.java#L291

    // dropping the attributes with a value of NULL here...

    val rawParts = s.split(Pom.extraAttributeSeparator).toSeq

    val partsOrError =
      if (rawParts.length % 2 == 0) {
        val malformed = rawParts.filter(!_.startsWith(Pom.extraAttributePrefix))
        if (malformed.isEmpty)
          Right(rawParts.map(_.drop(Pom.extraAttributePrefix.length)))
        else
          Left(
            s"Malformed attributes ${malformed.map("'" + _ + "'").mkString(", ")} in extra attributes '$s'"
          )
      }
      else
        Left(s"Malformed extra attributes '$s'")

    def attrFrom(attrs: Map[String, String], name: String): Either[String, String] =
      attrs
        .get(name)
        .toRight(s"$name not found in extra attributes '$s'")

    for {
      parts <- partsOrError
      attrs = parts
        .grouped(2)
        .collect {
          // ignore NULL values and info attributes
          case Seq(k, v) if v != "NULL" && !k.startsWith("e:info.") =>
            k.stripPrefix(Pom.extraAttributeDropPrefix) -> v
        }
        .toMap
      org     <- attrFrom(attrs, Pom.extraAttributeOrg).map(Organization(_))
      name    <- attrFrom(attrs, Pom.extraAttributeName).map(ModuleName(_))
      version <- attrFrom(attrs, Pom.extraAttributeVersion)
    } yield {
      val remainingAttrs = attrs.view.filterKeys(!Pom.extraAttributeBase(_)).toMap
      ((Module(org, name, Map.empty), VersionConstraint0(version)), remainingAttrs)
    }
  }

  private def getSbtCrossVersion(attributes: Map[String, String]): Option[String] =
    for {
      sbtVersion   <- attributes.get("sbtVersion")
      scalaVersion <- attributes.get("scalaVersion")
    } yield s"_${scalaVersion}_$sbtVersion"

  private def adaptProject(project: Project): Either[String, Project] =
    for {
      extraAttrs <- project.properties
        .collectFirst { case ("extraDependencyAttributes", s) => extraAttributes(s) }
        .getOrElse(Right(Map.empty[(Module, VersionConstraint0), Map[String, String]]))
    } yield {

      val adaptedDependencies = project.dependencies0.map {
        case (config, dep0) =>
          val dep = extraAttrs.get(dep0.moduleVersionConstraint).fold(dep0) { attrs =>
            // For an sbt plugin, we remove the suffix from the name and we add the sbtVersion
            // and scalaVersion attributes.
            val moduleWithAttrs = getSbtCrossVersion(attrs)
              .fold(dep0.module) { sbtCrossVersion =>
                val sttripedName = dep0.module.name.value.stripSuffix(sbtCrossVersion)
                dep0.module.withName(ModuleName(sttripedName))
              }
              .withAttributes(attrs)
            dep0.withModule(moduleWithAttrs)
          }
          config -> dep
      }

      project.withDependencies0(adaptedDependencies)
    }
}

@data(apply = false) class SbtMavenRepository(
  val root: String,
  val authentication: Option[Authentication] = None,
  val changing: Option[Boolean] = None,
  override val versionsCheckHasModule: Boolean = true,
  @since("2.1.25")
  checkModule: Boolean = false
) extends MavenRepositoryLike.WithModuleSupport with Repository.VersionApi { self =>
  import SbtMavenRepository._

  private val internal =
    new MavenRepositoryInternal(root, authentication, changing, checkModule) {

      override def moduleDirectory(module: Module): String =
        self.moduleDirectory(module)

      override def postProcessProject(project: Project): Either[String, Project] =
        super.postProcessProject(project)
          .flatMap(SbtMavenRepository.adaptProject)

      override def fetchArtifact[F[_]](
        module: Module,
        version: Version0,
        versioningValue: Option[Version0],
        fetch: Repository.Fetch[F]
      )(implicit F: Monad[F]): EitherT[F, String, Project] = {
        val directoryPath = moduleVersionPath(module, version)

        def tryFetch(artifactName: String): EitherT[F, String, Project] =
          fetchArtifactForModuleName(module, artifactName, version, versioningValue, fetch)

        SbtMavenRepository.getSbtCrossVersion(module.attributes) match {
          case Some(crossVersion) =>
            /** In case of an sbt plugin, for instance org.example:example:1.0.0 with
              * extra-attributes scalaVersion->2.12 and sbtVersion->1.0, we first try the valid
              * Maven pattern 'org/example/example_2.12_1.0/1.0.0/example_2.12_1.0-1.0.0-jar' then
              * the legacy pattern 'org/example/example_2.12_1.0/1.0.0/example-1.0.0-jar`
              */
            tryFetch(module.name.value + crossVersion).orElse(tryFetch(module.name.value))
          case None => tryFetch(module.name.value)
        }
      }

      override def tryListVersions(module: Module): Boolean =
        changing.forall(!_) && SbtMavenRepository.getSbtCrossVersion(module.attributes).isDefined
    }

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
    SbtMavenRepository.getSbtCrossVersion(module.attributes)
      .fold(module.name.value)(crossVersion => module.name.value + crossVersion)

  def urlFor(path: Seq[String], isDir: Boolean = false): String =
    internal.urlFor(path, isDir)

  def artifactFor(url: String, changing: Boolean): Artifact =
    internal.artifactFor(url, changing)

  def withChanging(changing: Boolean): SbtMavenRepository =
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
