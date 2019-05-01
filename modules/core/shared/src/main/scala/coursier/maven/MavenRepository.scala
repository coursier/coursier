package coursier.maven

import coursier.core._
import coursier.core.compatibility.encodeURIComponent
import coursier.util.{EitherT, Monad, WebPage}

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

  private def dirModuleName(module: Module, sbtAttrStub: Boolean): String =
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

}

final case class MavenRepository(
  root: String,
  changing: Option[Boolean] = None,
  /** Hackish hack for sbt plugins mainly - what this does really sucks */
  sbtAttrStub: Boolean = true,
  authentication: Option[Authentication] = None
) extends Repository {

  import Repository._
  import MavenRepository._

  // only used during benchmarks
  private[coursier] var useSaxParser = true

  // FIXME Ideally, we should silently drop a '/' suffix from `root`
  // so that
  //   MavenRepository("http://foo.com/repo") == MavenRepository("http://foo.com/repo/")
  private[maven] val root0 = if (root.endsWith("/")) root else root + "/"

  private def modulePath(module: Module): Seq[String] =
    module.organization.value.split('.').toSeq :+ dirModuleName(module, sbtAttrStub)

  private def moduleVersionPath(module: Module, version: String): Seq[String] =
    modulePath(module) :+ toBaseVersion(version)

  private[maven] def urlFor(path: Seq[String], isDir: Boolean = false): String =
    root0 + {
      if (isDir)
        path.map(encodeURIComponent).map(_ + "/").mkString
      else
        path.map(encodeURIComponent).mkString("/")
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

  def versionsArtifact(module: Module): Option[Artifact] = {

    val path = module.organization.value.split('.').toSeq ++ Seq(
      dirModuleName(module, sbtAttrStub),
      "maven-metadata.xml"
    )

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

      val files = WebPage.listFiles(listingUrl, listing)
      val rawVersions = WebPage.listDirectories(listingUrl, listing)

      val res =
        if (files.contains("maven-metadata.xml"))
          Left("maven-metadata.xml found, not listing version from directory listing")
        else if (rawVersions.isEmpty)
          Left(s"No versions found at $listingUrl")
        else {
          val parsedVersions = rawVersions.map(Version(_))
          val nonPreVersions = parsedVersions.filter(_.items.forall {
            case q: Version.Qualifier => q.level >= 0
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

  def versions[F[_]](
    module: Module,
    fetch: Repository.Fetch[F]
  )(implicit
    F: Monad[F]
  ): EitherT[F, String, (Versions, String)] =
    EitherT(
      versionsArtifact(module) match {
        case None => F.point(Left("Not supported"))
        case Some(artifact) =>
          F.map(fetch(artifact).run) { eitherStr =>
            for {
              str <- eitherStr.right
              xml <- compatibility.xmlParseDom(str).right
              _ <- (if (xml.label == "metadata") Right(()) else Left("Metadata not found")).right
              versions <- Pom.versions(xml).right
            } yield (versions, artifact.url)
          }
      }
    )

  def snapshotVersioning[F[_]](
    module: Module,
    version: String,
    fetch: Repository.Fetch[F]
  )(implicit
    F: Monad[F]
  ): EitherT[F, String, SnapshotVersioning] = {

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
  }

  def findNoInterval[F[_]](
    module: Module,
    version: String,
    fetch: Repository.Fetch[F]
  )(implicit
    F: Monad[F]
  ): EitherT[F, String, Project] =
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
      F.map(res)(_.right.map(proj => proj.copy(actualVersionOpt = Some(version))))
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

  def find[F[_]](
    module: Module,
    version: String,
    fetch: Repository.Fetch[F]
  )(implicit
    F: Monad[F]
  ): EitherT[F, String, (Artifact.Source, Project)] = {

    val versionsF = {
      val v = versions(module, fetch)
      if (changing.forall(!_) && module.attributes.contains("scalaVersion") && module.attributes.contains("sbtVersion"))
        versionsFromListing(module, fetch).orElse(v)
      else
        v
    }

    def fromEitherVersion(eitherVersion: Either[String, String], versions0: Versions):  EitherT[F, String, (Artifact.Source, Project)] =
      eitherVersion match {
        case Left(reason) => EitherT[F, String, (Artifact.Source, Project)](F.point(Left(reason)))
        case Right(version0) =>
          findNoInterval(module, version0, fetch)
            .map(_.copy(versions = Some(versions0)))
            .map((this, _))
      }

    Parse.versionInterval(version)
      .orElse(Parse.multiVersionInterval(version))
      .orElse(Parse.ivyLatestSubRevisionInterval(version))
      .filter(_.isValid) match {
        case None =>
          if (version == "LATEST" || version == "latest.integration")
            versionsF.flatMap {
              case (versions0, versionsUrl) =>
                val eitherVersion =
                  Some(versions0.latest).filter(_.nonEmpty)
                    .orElse(Some(versions0.release).filter(_.nonEmpty))
                    .toRight(s"No latest or release version found in $versionsUrl")

                fromEitherVersion(eitherVersion, versions0)
            }
          else if (version == "RELEASE" || version == "latest.release")
            versionsF.flatMap {
              case (versions0, versionsUrl) =>
                val eitherVersion =
                  Some(versions0.release).filter(_.nonEmpty)
                    .toRight(s"No release version found in $versionsUrl")

                fromEitherVersion(eitherVersion, versions0)
            }
          else
            findNoInterval(module, version, fetch).map((this, _))
        case Some(itv) =>
          versionsF.flatMap {
            case (versions0, versionsUrl) =>
              val eitherVersion = {
                val release = Version(versions0.release)

                if (itv.contains(release)) Right(versions0.release)
                else {
                  val inInterval = versions0.available
                    .map(Version(_))
                    .filter(itv.contains)

                  if (inInterval.isEmpty) Left(s"No version found for $version in $versionsUrl")
                  else Right(inInterval.max.repr)
                }
              }

              fromEitherVersion(eitherVersion, versions0)
          }
    }
  }


  private def artifacts0(
    dependency: Dependency,
    project: Project,
    overrideClassifiers: Option[Seq[Classifier]]
  ): Seq[(Attributes, Artifact)] = {

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

      val artifact = Artifact(
        root0 + path.mkString("/"),
        Map.empty,
        Map.empty,
        changing = changing0,
        optional = true,
        authentication = authentication
      )
        .withDefaultChecksums
        .withDefaultSignature

      (publication.attributes, artifact)
    }

    val (_, metadataArtifact) = artifactOf(Publication(dependency.module.name.value, Type.pom, Extension.pom, Classifier.empty))

    def artifactWithExtra(publication: Publication) = {
      val (attr, artifact) = artifactOf(publication)
      (attr, artifact.copy(
        extra = artifact.extra + ("metadata" -> metadataArtifact)
      ))
    }

    lazy val defaultPublications = {

      val packagingPublicationOpt = project
        .packagingOpt
        .filter(_ => dependency.attributes.isEmpty)
        .map { packaging =>
          Publication(
            dependency.module.name.value,
            packaging,
            MavenAttributes.typeExtension(packaging),
            MavenAttributes.typeDefaultClassifier(packaging)
          )
        }

      val types =
        if (dependency.attributes.`type`.isEmpty) {
          if (dependency.configuration == Configuration.test)
            Seq(Type.jar, Type.testJar)
          else
            Seq(Type.jar)
        } else
          Seq(dependency.attributes.`type`)

      val extraPubs = types.map { type0 =>

        val ext = MavenAttributes.typeExtension(type0)

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
          dependency.module.name.value,
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
  ): Seq[(Attributes, Artifact)] =
    if (project.relocated)
      Nil
    else
      artifacts0(dependency, project, overrideClassifiers)

  override def completeOpt[F[_]: Monad](fetch: Repository.Fetch[F]): Some[Repository.Complete[F]] =
    Some(MavenComplete(this, fetch, Monad[F]))

}
