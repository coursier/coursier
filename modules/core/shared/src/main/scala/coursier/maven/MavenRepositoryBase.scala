package coursier.maven

import coursier.core._
import coursier.core.compatibility.encodeURIComponent
import coursier.util.{Artifact, EitherT, Monad, WebPage}
import coursier.util.Monad.ops._
import dataclass._

import scala.collection.compat._
import coursier.util.Xml

trait MavenRepositoryBase extends Repository {
  import Repository._
  import MavenRepositoryBase._

  def authentication: Option[Authentication]
  def changing: Option[Boolean]
  def root: String

  private[coursier] def moduleDirectory(module: Module): String
  override def repr: String = root

  // only used during benchmarks
  private[coursier] var useSaxParser = true

  protected def rawPomParser: Pom
  protected def saxParser: PomParser

  private def modulePath(module: Module): Seq[String] =
    module.organization.value.split('.').toSeq :+ moduleDirectory(module)

  protected def moduleVersionPath(module: Module, version: String): Seq[String] =
    modulePath(module) :+ toBaseVersion(version)

  private[maven] def urlFor(path: Seq[String], isDir: Boolean = false): String = {
    val b = new StringBuilder(root)
    b += '/'

    val it      = path.iterator
    var isFirst = true
    while (it.hasNext) {
      if (!isDir)
        if (isFirst)
          isFirst = false
        else
          b += '/'
      b ++= it.next()
      if (isDir)
        b += '/'
    }

    b.result()
  }

  protected def projectArtifact(path: Seq[String], version: String): Artifact =
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

  private def versionsArtifact(module: Module): Artifact = {

    val path = modulePath(module) :+ "maven-metadata.xml"

    val artifact =
      Artifact(
        urlFor(path),
        Map.empty,
        Map("cache-errors" -> Artifact(
          "",
          Map.empty,
          Map.empty,
          changing = false,
          optional = false,
          None
        )),
        changing = true,
        optional = false,
        authentication = authentication
      )
        .withDefaultChecksums
        .withDefaultSignature

    artifact
  }

  def actualSnapshotVersioningArtifact(
    module: Module,
    version: String
  ): Artifact = {
    val path = moduleVersionPath(module, version) :+ "maven-metadata.xml"

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
  }

  @deprecated("Use actualSnapshotVersioningArtifact instead", "2.1.0-M2")
  def snapshotVersioningArtifact(
    module: Module,
    version: String
  ): Option[Artifact] =
    Some(actualSnapshotVersioningArtifact(module, version))

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

      val files       = WebPage.listFiles(listingUrl, listing).toVector
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
            case _              => true
          })

          val latest  = parsedVersions.max
          val release = if (nonPreVersions.nonEmpty) nonPreVersions.max else latest
          Right(Versions(
            latest.repr,
            release.repr,
            parsedVersions.map(_.repr).toList,
            None
          ))
        }

      EitherT(F.point(res.map((_, listingUrl))))
    }
  }

  protected def tryListVersions(module: Module): Boolean = changing.forall(!_)

  override protected def fetchVersions[F[_]](
    module: Module,
    fetch: Repository.Fetch[F]
  )(implicit
    F: Monad[F]
  ): EitherT[F, String, (Versions, String)] = {

    def viaMetadata = EitherT[F, String, (Versions, String)] {
      val artifact = versionsArtifact(module)
      fetch(artifact).run.map { eitherStr =>
        for {
          str      <- eitherStr
          xml      <- compatibility.xmlParseDom(str)
          _        <- if (xml.label == "metadata") Right(()) else Left("Metadata not found")
          versions <- Pom.versions(xml)
        } yield (versions, artifact.url)
      }
    }

    if (tryListVersions(module))
      versionsFromListing(module, fetch).orElse(viaMetadata)
    else viaMetadata
  }

  def snapshotVersioning[F[_]](
    module: Module,
    version: String,
    fetch: Repository.Fetch[F]
  )(implicit
    F: Monad[F]
  ): EitherT[F, String, SnapshotVersioning] = {
    val artifact = actualSnapshotVersioningArtifact(module, version)
    val task = fetch(artifact).run.map { eitherStr =>
      for {
        str                <- eitherStr
        xml                <- compatibility.xmlParseDom(str)
        _                  <- if (xml.label == "metadata") Right(()) else Left("Metadata not found")
        snapshotVersioning <- Pom.snapshotVersioning(xml)
      } yield snapshotVersioning
    }
    EitherT(task)
  }

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
                .map(_.withSnapshotVersioning(Some(snapshotVersioning)))
          }
        }

      val res = findVersioning(module, version, None, fetch).run.flatMap { eitherProj =>
        if (eitherProj.isLeft && isSnapshot(version))
          withSnapshotVersioning.run.map(eitherProj0 =>
            if (eitherProj0.isLeft)
              eitherProj
            else
              eitherProj0
          )
        else
          F.point(eitherProj)
      }

      // keep exact version used to get metadata, in case the one inside the metadata is wrong
      res.map(_.map(proj => (this, proj.withActualVersionOpt(Some(version)))))
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

  protected def fetchArtifact[F[_]](
    module: Module,
    version: String,
    versioningValue: Option[String],
    fetch: Repository.Fetch[F]
  )(implicit F: Monad[F]): EitherT[F, String, Project]

  protected def parsePom[F[_]](str: String)(implicit F: Monad[F]): EitherT[F, String, Project] =
    EitherT.fromEither {
      if (useSaxParser)
        coursier.core.compatibility.xmlParseSax(str, saxParser).project
      else
        for {
          xml  <- compatibility.xmlParseDom(str)
          _    <- if (xml.label == "project") Right(()) else Left("Project definition not found")
          proj <- rawPomParser.project(xml)
        } yield proj
    }

  def findVersioning[F[_]](
    module: Module,
    version: String,
    versioningValue: Option[String],
    fetch: Repository.Fetch[F]
  )(implicit
    F: Monad[F]
  ): EitherT[F, String, Project] =
    fetchArtifact(module, version, versioningValue, fetch)
      .map { proj0 =>
        Pom.addOptionalDependenciesInConfig(
          proj0
            .withActualVersionOpt(Some(version))
            .withConfigurations(defaultConfigurations),
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
        val tpe = MavenAttributes.typeDefaultClassifier(packaging)
        val ext = MavenAttributes.typeExtension(packaging)
        (tpe, ext) -> packaging
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

      val path =
        moduleVersionPath(dependency.module, project.actualVersion) :+
          project.module.name.value +
          "-" +
          versioning.getOrElse(project.actualVersion) +
          Some(publication.classifier.value).filter(_.nonEmpty).map("-" + _).mkString +
          "." +
          publication.ext.value

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

    val metadataArtifact = artifactOf(
      Publication(
        dependency.module.name.value,
        Type.pom,
        Extension.pom,
        Classifier.empty
      )
    )

    def artifactWithExtra(publication: Publication) = {
      val artifact = artifactOf(publication)
      artifact.withExtra(
        artifact.extra + ("metadata" -> metadataArtifact)
      )
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
        // this ignores publication.ext if publication.`type` is empty… should we?
        if (dependency.publication.`type`.isEmpty)
          if (dependency.configuration == Configuration.test)
            Seq((Type.jar, Extension.empty), (Type.testJar, Extension.empty))
          else
            Seq((Type.jar, Extension.empty))
        else
          Seq((dependency.publication.`type`, dependency.publication.ext))

      val extraPubs = types.map {
        case (type0, ext0) =>
          val ext = if (ext0.isEmpty) MavenAttributes.typeExtension(type0) else ext0

          val classifier =
            if (dependency.attributes.classifier.isEmpty)
              MavenAttributes.typeDefaultClassifier(type0)
            else
              dependency.attributes.classifier

          val tpe =
            if (dependency.publication.`type`.isEmpty)
              packagingTpeMap.getOrElse(
                (classifier, ext),
                MavenAttributes.classifierExtensionDefaultTypeOpt(classifier, ext)
                  .getOrElse(ext.asType)
              )
            else
              type0

          val optional = dependency.publication.isEmpty

          Publication(
            name,
            tpe,
            ext,
            classifier
          ) -> optional
      }

      val allPubs = packagingPublicationOpt.map(_ -> true).toSeq ++ extraPubs
      val optional = allPubs
        .groupBy(_._1)
        .view
        .mapValues(_.map(_._2).forall(identity))
        .iterator
        .toMap
      allPubs.map(_._1).distinct.map { pub =>
        (pub, optional(pub))
      }
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
              MavenAttributes.classifierExtensionDefaultTypeOpt(classifier, ext)
                .getOrElse(ext.asType)
            )

            Seq(
              Publication(
                dependency.module.name.value,
                tpe,
                ext,
                classifier
              ) -> true
            )
          }
        }
      }
      .map {
        case (pub, opt) =>
          val a = artifactWithExtra(pub).withOptional(opt)
          (pub, a)
      }
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

private[coursier] object MavenRepositoryBase {
  val SnapshotTimestamp = "(.*-)?[0-9]{8}\\.[0-9]{6}-[0-9]+".r

  def isSnapshot(version: String): Boolean =
    version.endsWith("SNAPSHOT") || SnapshotTimestamp.pattern.matcher(version).matches()

  def toBaseVersion(version: String): String =
    version match {
      case SnapshotTimestamp(null) => "SNAPSHOT"
      case SnapshotTimestamp(base) => base + "SNAPSHOT"
      case _                       => version
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
      .find { v =>
        (v.classifier == classifier || v.classifier == Classifier("*")) &&
        (v.extension == extension || v.extension == Extension("*"))
      }
      .map(_.value)
      .filter(_.nonEmpty)

  val defaultConfigurations = Map(
    Configuration.compile -> Seq.empty,
    Configuration.runtime -> Seq(Configuration.compile),
    Configuration.default -> Seq(Configuration.runtime),
    Configuration.test    -> Seq(Configuration.runtime)
  )
}
