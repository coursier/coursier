package coursier.maven

import com.github.plokhotnyuk.jsoniter_scala.core.{JsonReaderException, readFromString}
import coursier.core.{
  Authentication,
  Classifier,
  Configuration,
  Dependency,
  Extension,
  Module,
  Project,
  Publication,
  Repository,
  SnapshotVersioning,
  Type,
  VariantPublication,
  Versions
}
import coursier.util.{Artifact, EitherT, Monad, WebPage}
import coursier.util.Monad.ops._
import coursier.version.Version

import scala.collection.compat._
import coursier.core.VariantSelector
import java.net.URI

private[coursier] class MavenRepositoryInternal(
  root: String,
  authentication: Option[Authentication],
  changing: Option[Boolean],
  checkModule: Boolean
) {
  import Repository._
  import MavenRepositoryInternal._

  def moduleDirectory(module: Module): String =
    module.name.value

  // only used during benchmarks
  private[coursier] var useSaxParser = true

  private def modulePath(module: Module): Seq[String] =
    module.organization.value.split('.').toSeq :+ moduleDirectory(module)

  def moduleVersionPath(module: Module, version: Version): Seq[String] =
    modulePath(module) :+ toBaseVersion(version)

  def urlFor(path: Seq[String], isDir: Boolean = false): String = {
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

  def projectArtifact(path: Seq[String], version: Version): Artifact =
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

  private def actualSnapshotVersioningArtifact(
    module: Module,
    version: Version
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
            latest,
            release,
            parsedVersions.toList,
            None
          ))
        }

      EitherT(F.point(res.map((_, listingUrl))))
    }
  }

  def tryListVersions(module: Module): Boolean = changing.forall(!_)

  def postProcessProject(project: Project): Either[String, Project] =
    Right {
      Pom.addOptionalDependenciesInConfig(
        project.withConfigurations(defaultConfigurations),
        Set(Configuration.empty, Configuration.default),
        Configuration.optional
      )
    }

  def fetchVersions[F[_]](
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
          xml      <- coursier.core.compatibility.xmlParseDom(str)
          _        <- if (xml.label == "metadata") Right(()) else Left("Metadata not found")
          versions <- Pom.versions(xml)
        } yield (versions, artifact.url)
      }
    }

    if (tryListVersions(module))
      versionsFromListing(module, fetch).orElse(viaMetadata)
    else viaMetadata
  }

  private def snapshotVersioning[F[_]](
    module: Module,
    version: Version,
    fetch: Repository.Fetch[F]
  )(implicit
    F: Monad[F]
  ): EitherT[F, String, SnapshotVersioning] = {
    val artifact = actualSnapshotVersioningArtifact(module, version)
    val task: F[Either[String, SnapshotVersioning]] = fetch(artifact).run.map { eitherStr =>
      for {
        str                <- eitherStr
        xml                <- coursier.core.compatibility.xmlParseDom(str)
        _                  <- if (xml.label == "metadata") Right(()) else Left("Metadata not found")
        snapshotVersioning <- Pom.snapshotVersioning(xml)
      } yield snapshotVersioning
    }
    EitherT(task)
  }

  def find[F[_]](
    module: Module,
    version: Version,
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
      res.map(_.map { proj =>
        if (proj.version0 == version) proj
        else proj.withActualVersionOpt0(Some(version))
      })
    }

  def artifactFor(url: String, changing: Boolean): Artifact =
    Artifact(
      url,
      Map.empty,
      Map.empty,
      changing = changing,
      optional = false,
      authentication
    )

  /** Allows to adjust the module name that appears in the file name
    *
    * Doesn't change the one in the directory path
    *
    * Useful for some weird sbt plugin stuff
    */
  protected def fetchArtifactForModuleName[F[_]](
    module: Module,
    moduleNameInFileName: String,
    version: Version,
    versioningValue: Option[Version],
    fetch: Repository.Fetch[F]
  )(implicit F: Monad[F]): EitherT[F, String, Project] = {
    val directoryPath = moduleVersionPath(module, version)
    def pathFor(ext: String) =
      directoryPath :+ s"$moduleNameInFileName-${versioningValue.getOrElse(version).asString}.$ext"
    def pomProjectTask = {
      val pomArtifact = projectArtifact(pathFor("pom"), version)
      fetch(pomArtifact).flatMap(parsePom(_))
    }
    if (checkModule) {
      val moduleProjectTask: EitherT[F, String, Either[String, Project]] =
        EitherT {
          val moduleArtifact = projectArtifact(pathFor("module"), version)
          fetch(moduleArtifact).run.flatMap {
            case Left(err) =>
              F.point(Right(Left(err)))
            case Right(content) =>
              parseModule(module, content, moduleArtifact.url).run.map(_.map(Right(_)))
          }
        }
      moduleProjectTask.flatMap {
        case Left(moduleFetchError) =>
          pomProjectTask.leftMap { pomError =>
            Seq(moduleFetchError, pomError).mkString(System.lineSeparator())
          }
        case Right(projectFromModule) => EitherT.point(projectFromModule)
      }
    }
    else
      pomProjectTask
  }

  def fetchArtifact[F[_]](
    module: Module,
    version: Version,
    versioningValue: Option[Version],
    fetch: Repository.Fetch[F]
  )(implicit F: Monad[F]): EitherT[F, String, Project] =
    fetchArtifactForModuleName(module, module.name.value, version, versioningValue, fetch)(F)

  def parsePom[F[_]](str: String)(implicit F: Monad[F]): EitherT[F, String, Project] =
    EitherT.fromEither {
      val maybeProj =
        if (useSaxParser)
          coursier.core.compatibility.xmlParseSax(str, new PomParser).project
        else
          for {
            xml  <- coursier.core.compatibility.xmlParseDom(str)
            _    <- if (xml.label == "project") Right(()) else Left("Project definition not found")
            proj <- Pom.project(xml)
          } yield proj

      for {
        proj      <- maybeProj
        finalProj <- postProcessProject(proj)
      } yield finalProj
    }

  private def parseModule[F[_]](module: Module, str: String, uri: String)(implicit
    F: Monad[F]
  ): EitherT[F, String, Project] =
    EitherT.fromEither {
      val res =
        try Right(readFromString(str)(GradleModule.codec))
        catch {
          case ex: JsonReaderException =>
            Left(s"Error reading $uri: $ex")
        }

      res.map { gradleMod =>
        val project = gradleMod.project
        if (project.module == module) project
        else project.withModule(module)
      }
    }

  private def findVersioning[F[_]](
    module: Module,
    version: Version,
    versioningValue: Option[Version],
    fetch: Repository.Fetch[F]
  )(implicit
    F: Monad[F]
  ): EitherT[F, String, Project] =
    fetchArtifact(module, version, versioningValue, fetch).map { proj =>
      if (proj.version0 == version) proj
      else proj.withActualVersionOpt0(Some(version))
    }

  private def artifacts0(
    dependency: Dependency,
    project: Project,
    isTest: Boolean,
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
        moduleVersionPath(dependency.module, project.actualVersion0) :+
          project.module.name.value +
          "-" +
          versioning.getOrElse(project.actualVersion0).asString +
          Some(publication.classifier.value).filter(_.nonEmpty).map("-" + _).mkString +
          "." +
          publication.ext.value

      val changing0 = changing.getOrElse(isSnapshot(project.actualVersion0))

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
          if (isTest)
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

      val allPubs = packagingPublicationOpt.map(_ -> false).toSeq ++ extraPubs
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
      dependency.variantSelector match {
        case c: VariantSelector.ConfigurationBased =>
          artifacts0(
            dependency,
            project,
            c.configuration == Configuration.test,
            overrideClassifiers
          )
        case attr: VariantSelector.AttributesBased =>
          if (project.variants.isEmpty) {
            val isTest = attr.equivalentConfiguration.exists(_ == Configuration.test)
            artifacts0(dependency, project, isTest, overrideClassifiers)
          }
          else
            Nil
      }

  def moduleArtifacts(
    dependency: Dependency,
    project: Project
  ): Seq[(VariantPublication, Artifact)] =
    dependency.variantSelector match {
      case _: VariantSelector.ConfigurationBased => Nil
      case a: VariantSelector.AttributesBased =>
        if (project.variants.isEmpty) Nil
        else {
          val attr = project.variantFor(a)
            .toTry.get // things shouldn't throw at this point
          project.variantPublications.getOrElse(attr, Nil).map { pub =>
            val baseUri = new URI(pub.url)
            val uri =
              if (baseUri.isAbsolute) baseUri
              else
                new URI(
                  urlFor(
                    moduleVersionPath(dependency.module, project.actualVersion0),
                    isDir = true
                  )
                ).resolve(baseUri)
            val art = Artifact(uri.toASCIIString).withOptional(false)
            (pub, art)
          }
        }
    }
}

private[coursier] object MavenRepositoryInternal {
  val SnapshotTimestamp = "(.*-)?[0-9]{8}\\.[0-9]{6}-[0-9]+".r

  def isSnapshot(version: Version): Boolean =
    version.repr.endsWith("SNAPSHOT") || SnapshotTimestamp.pattern.matcher(version.repr).matches()

  def toBaseVersion(version: Version): String =
    version.repr match {
      case SnapshotTimestamp(null) => "SNAPSHOT"
      case SnapshotTimestamp(base) => base + "SNAPSHOT"
      case _                       => version.repr
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
  ): Option[Version] =
    snapshotVersioning
      .snapshotVersions
      .find { v =>
        (v.classifier == classifier || v.classifier == Classifier("*")) &&
        (v.extension == extension || v.extension == Extension("*"))
      }
      .map(_.value0)
      .filter(_.asString.nonEmpty)

  val defaultConfigurations = Map(
    Configuration.compile -> Seq.empty,
    Configuration.runtime -> Seq(Configuration.compile),
    Configuration.default -> Seq(Configuration.runtime),
    Configuration.test    -> Seq(Configuration.runtime)
  )
}
