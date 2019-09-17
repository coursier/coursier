package coursier.maven

import coursier.core._
import coursier.core.compatibility.encodeURIComponent
import coursier.util.{Artifact, EitherT, Monad, WebPage}

object MavenRepository {
  val SnapshotTimestamp = "(.*-)?[0-9]{8}\\.[0-9]{6}-[0-9]+".r

  def isSnapshot(version: String): Boolean =
    version.endsWith("SNAPSHOT") || SnapshotTimestamp.pattern.matcher(version).matches()

  def toBaseVersion(version: String): String = version match {
      case SnapshotTimestamp(null) => "SNAPSHOT"
      case SnapshotTimestamp(base) => base + "SNAPSHOT"
      case _ => version
    }

  def ivyLikePath(
    org: String,
    dirName: String,
    name: String,
    version: String,
    subDir: String,
    baseSuffix: String,
    ext: String
  ) =
    Seq(
      org,
      dirName,
      version,
      subDir,
      s"$name$baseSuffix.$ext"
    )

  def mavenVersioning(
    snapshotVersioning: SnapshotVersioning,
    classifier: Classifier,
    extension: Extension
  ): Option[String] =
    snapshotVersioning
      .snapshotVersions
      .find(v =>
        (v.classifier == classifier || v.classifier == Classifier("*")) &&
        (v.extension == extension || v.extension == Extension("*"))
       )
      .map(_.value)
      .filter(_.nonEmpty)


  val defaultConfigurations = Map(
    Configuration.compile -> Seq.empty,
    Configuration.runtime -> Seq(Configuration.compile),
    Configuration.default -> Seq(Configuration.runtime),
    Configuration.test -> Seq(Configuration.runtime)
  )

  private[coursier] def dirModuleName(module: Module, sbtAttrStub: Boolean): String =
    if (sbtAttrStub) {
      var name = module.name.value
      for (scalaVersion <- module.attributes.get("scalaVersion"))
        name = name + "_" + scalaVersion
      for (sbtVersion <- module.attributes.get("sbtVersion"))
        name = name + "_" + sbtVersion
      name
    } else
      module.name.value

  private[coursier] def parseRawPomSax(str: String): Either[String, Project] =
    coursier.core.compatibility.xmlParseSax(str, new PomParser)
      .project

  private[coursier] def parseRawPomDom(str: String): Either[String, Project] =
    for {
      xml <- compatibility.xmlParseDom(str).right
      _ <- (if (xml.label == "project") Right(()) else Left("Project definition not found")).right
      proj <- Pom.project(xml).right
    } yield proj


  def apply(
    root: String,
    changing: Option[Boolean] = None,
    sbtAttrStub: Boolean = true,
    authentication: Option[Authentication] = None
  ): MavenRepository =
    new MavenRepository(
      actualRoot(root),
      changing,
      sbtAttrStub,
      authentication,
      versionsCheckHasModule = true
    )

  private def actualRoot(root: String): String =
    root.stripSuffix("/")

}

final class MavenRepository private (
  val root: String,
  val changing: Option[Boolean],
  /** Hackish hack for sbt plugins mainly - that's some totally ad hoc stuff… */
  val sbtAttrStub: Boolean,
  val authentication: Option[Authentication],
  override val versionsCheckHasModule: Boolean
) extends Repository {

  override def equals(obj: Any): Boolean =
    obj match {
      case other: MavenRepository =>
        root == other.root &&
          changing == other.changing &&
          sbtAttrStub == other.sbtAttrStub &&
          authentication == other.authentication &&
          versionsCheckHasModule == other.versionsCheckHasModule
      case _ => false
    }

  override def hashCode(): Int = {
    var code = 17 + "coursier.maven.MavenRepository".##
    code = 37 * code + root.##
    code = 37 * code + changing.##
    code = 37 * code + sbtAttrStub.##
    code = 37 * code + authentication.##
    code = 37 * code + versionsCheckHasModule.##
    37 * code
  }

  override def toString: String =
    s"MavenRepository($root, $changing, $sbtAttrStub, $authentication, $versionsCheckHasModule)"

  private def copy0(
    root: String = root,
    changing: Option[Boolean] = changing,
    sbtAttrStub: Boolean = sbtAttrStub,
    authentication: Option[Authentication] = authentication,
    versionsCheckHasModule: Boolean = versionsCheckHasModule
  ): MavenRepository =
    new MavenRepository(
      MavenRepository.actualRoot(root),
      changing,
      sbtAttrStub,
      authentication,
      versionsCheckHasModule
    )

  @deprecated("Use the with* methods instead", "2.0.0-RC3")
  def copy(
    root: String = root,
    changing: Option[Boolean] = changing,
    sbtAttrStub: Boolean = sbtAttrStub,
    authentication: Option[Authentication] = authentication
  ): MavenRepository =
    copy0(root, changing, sbtAttrStub, authentication)

  def withRoot(root: String): MavenRepository =
    copy0(root = root)
  def withChanging(changingOpt: Option[Boolean]): MavenRepository =
    copy0(changing = changingOpt)
  def withChanging(changing: Boolean): MavenRepository =
    copy0(changing = Some(changing))
  def withSbtAttrStub(sbtAttrStub: Boolean): MavenRepository =
    copy0(sbtAttrStub = sbtAttrStub)
  def withAuthentication(authentication: Option[Authentication]): MavenRepository =
    copy0(authentication = authentication)
  def withVersionsCheckHasModule(versionsCheckHasModule: Boolean): MavenRepository =
    copy0(versionsCheckHasModule = versionsCheckHasModule)


  import Repository._
  import MavenRepository._

  override def repr: String =
    root

  // only used during benchmarks
  private[coursier] var useSaxParser = true

  private def modulePath(module: Module): Seq[String] =
    module.organization.value.split('.').toSeq :+ dirModuleName(module, sbtAttrStub)

  private def moduleVersionPath(module: Module, version: String): Seq[String] =
    modulePath(module) :+ toBaseVersion(version)

  private[maven] def urlFor(path: Seq[String], isDir: Boolean = false): String = {
    val b = new StringBuilder(root)
    b += '/'

    val it = path.iterator
    var isFirst = true
    while (it.hasNext) {
      if (!isDir) {
        if (isFirst)
          isFirst = false
        else
          b += '/'
      }
      b ++= it.next()
      if (isDir)
        b += '/'
    }

    b.result()
  }

  def projectArtifact(
    module: Module,
    version: String,
    versioningValue: Option[String]
  ): Artifact = {

    val path = moduleVersionPath(module, version) :+
      s"${module.name.value}-${versioningValue getOrElse version}.pom"

    Artifact(
      urlFor(path),
      Map.empty,
      Map.empty,
      changing = changing.getOrElse(isSnapshot(version)),
      optional = false,
      authentication = authentication
    )
    .withDefaultChecksums
    .withDefaultSignature
  }

  private def versionsArtifact(module: Module): Artifact = {

    val path = module.organization.value.split('.').toSeq ++ Seq(
      dirModuleName(module, sbtAttrStub),
      "maven-metadata.xml"
    )

    val artifact =
      Artifact(
        urlFor(path),
        Map.empty,
        Map("cache-errors" -> Artifact("", Map.empty, Map.empty, changing = false, optional = false, None)),
        changing = true,
        optional = false,
        authentication = authentication
      )
      .withDefaultChecksums
      .withDefaultSignature

    artifact
  }

  def snapshotVersioningArtifact(
    module: Module,
    version: String
  ): Option[Artifact] = {

    val path = moduleVersionPath(module, version) :+ "maven-metadata.xml"

    val artifact =
      Artifact(
        urlFor(path),
        Map.empty,
        Map.empty,
        changing = true,
        optional = false,
        authentication = authentication
      )
      .withDefaultChecksums
      .withDefaultSignature

    Some(artifact)
  }

  private def versionsFromListing[F[_]](
    module: Module,
    fetch: Repository.Fetch[F]
  )(implicit
    F: Monad[F]
  ): EitherT[F, String, (Versions, String)] = {

    val listingUrl = urlFor(modulePath(module)) + "/"

    // version listing -> changing (changes as new versions are released)
    val listingArtifact = artifactFor(listingUrl, changing = true)

    fetch(listingArtifact).flatMap { listing =>

      val files = WebPage.listFiles(listingUrl, listing).toVector
      val rawVersions = WebPage.listDirectories(listingUrl, listing).toVector

      val res =
        if (files.contains("maven-metadata.xml"))
          Left("maven-metadata.xml found, not listing version from directory listing")
        else if (rawVersions.isEmpty)
          Left(s"No versions found at $listingUrl")
        else {
          val parsedVersions = rawVersions.map(Version(_))
          val nonPreVersions = parsedVersions.filter(_.items.forall {
            case t: Version.Tag => !t.isPreRelease
            case _ => true
          })

          if (nonPreVersions.isEmpty)
            Left(s"Found only pre-versions at $listingUrl")
          else {
            val latest = nonPreVersions.max
            Right(Versions(
              latest.repr,
              latest.repr,
              nonPreVersions.map(_.repr).toList,
              None
            ))
          }
        }

      EitherT(F.point(res.right.map((_, listingUrl))))
    }
  }

  override protected def fetchVersions[F[_]](
    module: Module,
    fetch: Repository.Fetch[F]
  )(implicit
    F: Monad[F]
  ): EitherT[F, String, (Versions, String)] = {

    val viaMetadata = EitherT[F, String, (Versions, String)] {
      val artifact = versionsArtifact(module)
      F.map(fetch(artifact).run) { eitherStr =>
        for {
          str <- eitherStr.right
          xml <- compatibility.xmlParseDom(str).right
          _ <- (if (xml.label == "metadata") Right(()) else Left("Metadata not found")).right
          versions <- Pom.versions(xml).right
        } yield (versions, artifact.url)
      }
    }

    if (changing.forall(!_) && module.attributes.contains("scalaVersion") && module.attributes.contains("sbtVersion"))
      versionsFromListing(module, fetch).orElse(viaMetadata)
    else
      viaMetadata
  }

  def snapshotVersioning[F[_]](
    module: Module,
    version: String,
    fetch: Repository.Fetch[F]
  )(implicit
    F: Monad[F]
  ): EitherT[F, String, SnapshotVersioning] =
    EitherT(
      snapshotVersioningArtifact(module, version) match {
        case None => F.point(Left("Not supported"))
        case Some(artifact) =>
          F.map(fetch(artifact).run) { eitherStr =>
            for {
              str <- eitherStr.right
              xml <- compatibility.xmlParseDom(str).right
              _ <- (if (xml.label == "metadata") Right(()) else Left("Metadata not found")).right
              snapshotVersioning <- Pom.snapshotVersioning(xml).right
            } yield snapshotVersioning
          }
      }
    )

  def find[F[_]](
    module: Module,
    version: String,
    fetch: Repository.Fetch[F]
  )(implicit
    F: Monad[F]
  ): EitherT[F, String, (ArtifactSource, Project)] =
    EitherT {
      def withSnapshotVersioning =
        snapshotVersioning(module, version, fetch).flatMap { snapshotVersioning =>
          val versioningOption =
            mavenVersioning(snapshotVersioning, Classifier.empty, Extension.jar)
              .orElse(mavenVersioning(snapshotVersioning, Classifier.empty, Extension.pom))
              .orElse(mavenVersioning(snapshotVersioning, Classifier.empty, Extension.empty))

          versioningOption match {
            case None =>
              EitherT[F, String, Project](
                F.point(Left("No snapshot versioning value found"))
              )
            case versioning @ Some(_) =>
              findVersioning(module, version, versioning, fetch)
                .map(_.copy(snapshotVersioning = Some(snapshotVersioning)))
          }
        }

      val res = F.bind(findVersioning(module, version, None, fetch).run) { eitherProj =>
        if (eitherProj.isLeft && isSnapshot(version))
          F.map(withSnapshotVersioning.run)(eitherProj0 =>
            if (eitherProj0.isLeft)
              eitherProj
            else
              eitherProj0
          )
        else
          F.point(eitherProj)
      }

      // keep exact version used to get metadata, in case the one inside the metadata is wrong
      F.map(res)(_.right.map(proj => (this, proj.copy(actualVersionOpt = Some(version)))))
    }

  private[maven] def artifactFor(url: String, changing: Boolean) =
    Artifact(
      url,
      Map.empty,
      Map.empty,
      changing = changing,
      optional = false,
      authentication
    )

  def findVersioning[F[_]](
    module: Module,
    version: String,
    versioningValue: Option[String],
    fetch: Repository.Fetch[F]
  )(implicit
    F: Monad[F]
  ): EitherT[F, String, Project] = {


    val projectArtifact0 = projectArtifact(module, version, versioningValue)

    for {
      str <- fetch(projectArtifact0)
      proj0 <- EitherT(F.point[Either[String, Project]](if (useSaxParser) parseRawPomSax(str) else parseRawPomDom(str)))
    } yield
      Pom.addOptionalDependenciesInConfig(
        proj0.copy(
          actualVersionOpt = Some(version),
          configurations = defaultConfigurations
        ),
        Set(Configuration.empty, Configuration.default),
        Configuration.optional
      )
  }

  private def artifacts0(
    dependency: Dependency,
    project: Project,
    overrideClassifiers: Option[Seq[Classifier]]
  ): Seq[(Publication, Artifact)] = {

    val packagingTpeMap = project
      .packagingOpt
      .map { packaging =>
        (MavenAttributes.typeDefaultClassifier(packaging), MavenAttributes.typeExtension(packaging)) -> packaging
      }
      .toMap

    def artifactOf(publication: Publication) = {

      val versioning = project
        .snapshotVersioning
        .flatMap(versioning =>
          mavenVersioning(
            versioning,
            publication.classifier,
            MavenAttributes.typeExtension(publication.`type`)
          )
        )

      val path = dependency.module.organization.value.split('.').toSeq ++ Seq(
        MavenRepository.dirModuleName(dependency.module, sbtAttrStub),
        toBaseVersion(project.actualVersion),
        s"${dependency.module.name.value}-${versioning getOrElse project.actualVersion}${Some(publication.classifier.value).filter(_.nonEmpty).map("-" + _).mkString}.${publication.ext.value}"
      )

      val changing0 = changing.getOrElse(isSnapshot(project.actualVersion))

      Artifact(
        root + path.mkString("/", "/", ""),
        Map.empty,
        Map.empty,
        changing = changing0,
        optional = true,
        authentication = authentication
      )
        .withDefaultChecksums
        .withDefaultSignature
    }

    val metadataArtifact = artifactOf(Publication(dependency.module.name.value, Type.pom, Extension.pom, Classifier.empty))

    def artifactWithExtra(publication: Publication) = {
      val artifact = artifactOf(publication)
      val artifact0 = artifact.copy(
        extra = artifact.extra + ("metadata" -> metadataArtifact)
      )
      (publication, artifact0)
    }

    lazy val defaultPublications = {

      val name =
        if (dependency.publication.name.isEmpty)
          dependency.module.name.value
        else
          // no unit tests for that branch for now
          dependency.publication.name

      val packagingPublicationOpt = project
        .packagingOpt
        .filter(_ => dependency.attributes.isEmpty)
        .map { packaging =>
          Publication(
            name,
            packaging,
            MavenAttributes.typeExtension(packaging),
            MavenAttributes.typeDefaultClassifier(packaging)
          )
        }

      val types =
        // this ignore publication.ext if publication.`type` is empty… should we?
        if (dependency.publication.`type`.isEmpty) {
          if (dependency.configuration == Configuration.test)
            Seq((Type.jar, Extension.empty), (Type.testJar, Extension.empty))
          else
            Seq((Type.jar, Extension.empty))
        } else
          Seq((dependency.publication.`type`, dependency.publication.ext))

      val extraPubs = types.map {
        case (type0, ext0) =>

          val ext = if (ext0.isEmpty) MavenAttributes.typeExtension(type0) else ext0

          val classifier =
            if (dependency.attributes.classifier.isEmpty)
              MavenAttributes.typeDefaultClassifier(type0)
            else
              dependency.attributes.classifier

          val tpe = packagingTpeMap.getOrElse(
            (classifier, ext),
            MavenAttributes.classifierExtensionDefaultTypeOpt(classifier, ext).getOrElse(ext.asType)
          )

          Publication(
            name,
            tpe,
            ext,
            classifier
          )
      }

      (packagingPublicationOpt.toSeq ++ extraPubs)
        .distinct
    }

    overrideClassifiers
      .fold(defaultPublications) { classifiers =>
        classifiers.flatMap { classifier =>
          if (classifier == dependency.attributes.classifier)
            defaultPublications
          else {
            val ext = Extension.jar
            val tpe = packagingTpeMap.getOrElse(
              (classifier, ext),
              MavenAttributes.classifierExtensionDefaultTypeOpt(classifier, ext).getOrElse(ext.asType)
            )

            Seq(
              Publication(
                dependency.module.name.value,
                tpe,
                ext,
                classifier
              )
            )
          }
        }
      }
      .map(artifactWithExtra)
  }

  def artifacts(
    dependency: Dependency,
    project: Project,
    overrideClassifiers: Option[Seq[Classifier]]
  ): Seq[(Publication, Artifact)] =
    if (project.relocated)
      Nil
    else
      artifacts0(dependency, project, overrideClassifiers)

  override def completeOpt[F[_]: Monad](fetch: Repository.Fetch[F]): Some[Repository.Complete[F]] =
    Some(MavenComplete(this, fetch, Monad[F]))

}
