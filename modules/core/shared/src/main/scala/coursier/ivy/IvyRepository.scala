package coursier.ivy

import coursier.core._
import coursier.maven.MavenAttributes
import coursier.util.{EitherT, Monad, WebPage}

final case class IvyRepository(
  pattern: Pattern,
  metadataPatternOpt: Option[Pattern],
  changing: Option[Boolean],
  withChecksums: Boolean,
  withSignatures: Boolean,
  withArtifacts: Boolean,
  // hack for sbt putting infos in properties
  dropInfoAttributes: Boolean,
  authentication: Option[Authentication]
) extends Repository {

  def metadataPattern: Pattern = metadataPatternOpt.getOrElse(pattern)

  private[ivy] def patternUpTo(chunk: Pattern.Chunk): Option[Pattern] = {
    val idx = metadataPattern.chunks.indexWhere(_ == chunk)

    if (idx < 0)
      None
    else
      Some(Pattern(metadataPattern.chunks.take(idx)))
  }

  lazy val revisionListingPatternOpt: Option[Pattern] =
    patternUpTo(Pattern.Chunk.Var("revision"))

  import Repository._

  private[ivy] def orgVariables(org: Organization): Map[String, String] =
    Map(
      "organization" -> org.value,
      "organisation" -> org.value,
      "orgPath" -> org.value.replace('.', '/')
    )

  // See http://ant.apache.org/ivy/history/latest-milestone/concept.html for a
  // list of variables that should be supported.
  // Some are missing (branch, conf, originalName).
  private def variables(
    module: Module,
    versionOpt: Option[String],
    `type`: Type,
    artifact: String,
    ext: Extension,
    classifierOpt: Option[Classifier]
  ): Map[String, String] =
    orgVariables(module.organization) ++
    Seq(
      "module" -> module.name.value,
      "type" -> `type`.value,
      "artifact" -> artifact,
      "ext" -> ext.value
    ) ++
    module.attributes ++
    classifierOpt.map("classifier" -> _.value).toSeq ++
    versionOpt.map("revision" -> _).toSeq


  def artifacts(
    dependency: Dependency,
    project: Project,
    overrideClassifiers: Option[Seq[Classifier]]
  ): Seq[(Publication, Artifact)] =
    if (withArtifacts) {

      val retained =
        overrideClassifiers match {
          case None =>

            if (dependency.publication.name.nonEmpty) {
              val tpe =
                if (dependency.publication.`type`.isEmpty) Type.jar
                else dependency.publication.`type`
              val ext =
                if (dependency.publication.ext.isEmpty) MavenAttributes.typeExtension(tpe)
                else dependency.publication.ext
              Seq(
                dependency.publication.copy(`type` = tpe, ext = ext)
              )
            } else if (dependency.attributes.classifier.nonEmpty)
              // FIXME We're ignoring dependency.attributes.`type` in this case
              project.publications.collect {
                case (_, p) if p.classifier == dependency.attributes.classifier =>
                  p
              }
            else if (dependency.attributes.`type`.nonEmpty)
              project.publications.collect {
                case (conf, p)
                  if (conf == Configuration.all ||
                    conf == dependency.configuration ||
                    project.allConfigurations.getOrElse(dependency.configuration, Set.empty).contains(conf)) &&
                    (
                      p.`type` == dependency.attributes.`type` ||
                      (p.ext == dependency.attributes.`type`.asExtension && project.packagingOpt.toSeq.contains(p.`type`)) // wow
                    ) =>
                  p
              }
            else
              project.publications.collect {
                case (conf, p)
                  if conf == Configuration.all ||
                     conf == dependency.configuration ||
                     project.allConfigurations.getOrElse(dependency.configuration, Set.empty).contains(conf) =>
                  p
              }
          case Some(classifiers) =>
            val classifiersSet = classifiers.toSet
            project.publications.collect {
              case (_, p) if classifiersSet(p.classifier) =>
                p
            }
        }

      val retainedWithUrl = retained.distinct.flatMap { p =>
        pattern.substituteVariables(variables(
          dependency.module,
          Some(project.actualVersion),
          p.`type`,
          p.name,
          p.ext,
          Some(p.classifier).filter(_.nonEmpty)
        )).right.toSeq.toList.map(p -> _) // FIXME Validation errors are ignored
      }

      retainedWithUrl.map {
        case (p, url) =>

          var artifact = artifactFor(
            url,
            changing = changing.getOrElse(IvyRepository.isSnapshot(project.version))
          )

          if (withChecksums)
            artifact = artifact.withDefaultChecksums
          if (withSignatures)
            artifact = artifact.withDefaultSignature

          (p, artifact)
      }
    } else
      Nil

  private def artifactFor(url: String, changing: Boolean) =
    Artifact(
      url,
      Map.empty,
      Map.empty,
      changing = changing,
      optional = false,
      authentication
    )

  private[ivy] def listing[F[_]](
    listingPatternOpt: Option[Pattern],
    listingName: String,
    variables: Map[String, String],
    fetch: Repository.Fetch[F]
  )(implicit
    F: Monad[F]
  ): EitherT[F, String, Option[Seq[String]]] =
    listingPatternOpt match {
      case None =>
        EitherT(F.point(Right(None)))
      case Some(listingPattern) =>
        val listingUrl = listingPattern
          .substituteVariables(variables)
          .right
          .flatMap { s =>
            if (s.endsWith("/"))
              Right(s)
            else
              Left(s"Don't know how to list $listingName of ${metadataPattern.string}")
          }

        for {
          url <- EitherT(F.point(listingUrl))
          s <- fetch(artifactFor(url, changing = true))
        } yield Some(WebPage.listDirectories(url, s))
    }

  def availableVersions[F[_]](
    module: Module,
    fetch: Repository.Fetch[F]
  )(implicit
    F: Monad[F]
  ): EitherT[F, String, Option[Seq[Version]]] =
    listing(
      revisionListingPatternOpt,
      "revisions",
      variables(module, None, Type.ivy, "ivy", Extension("xml"), None),
      fetch
    ).map(_.map(_.map(Parse.version).collect { case Some(v) => v }))

  override def versions[F[_]](
    module: Module,
    fetch: Repository.Fetch[F]
  )(implicit
    F: Monad[F]
  ): EitherT[F, String, Versions] =
    availableVersions(module, fetch).map {
      case Some(l) if l.nonEmpty =>
        val latest = l.max.repr
        val release = {
          val l0 = l.filter(!_.repr.endsWith("SNAPSHOT"))
          if (l0.isEmpty)
            ""
          else
            l0.max.repr
        }
        Versions(
          latest,
          release,
          l.map(_.repr).toList,
          None
        )
      case _ =>
        Versions.empty
    }

  def find[F[_]](
    module: Module,
    version: String,
    fetch: Repository.Fetch[F]
  )(implicit
    F: Monad[F]
  ): EitherT[F, String, (Artifact.Source, Project)] = {

    def fromVersions(filter: Version => Boolean, versions: Seq[Version]) = {
      val versionsInItv = versions.filter(filter)

      if (versionsInItv.isEmpty)
        EitherT(
          F.point[Either[String, (Artifact.Source, Project)]](Left(s"No version found for $version"))
        )
      else {
        val version0 = versionsInItv.max
        findNoInverval(module, version0.repr, fetch)
      }
    }

    Parse.versionInterval(version)
      .orElse(Parse.multiVersionInterval(version))
      .orElse(Parse.ivyLatestSubRevisionInterval(version))
      .filter(_.isValid) match {
      case None =>
        if (version == "latest.integration" || version == "latest.release") {
          val acceptChanging = version == "latest.integration"
          val filter: Version => Boolean =
            if (acceptChanging) _ => true
            else v => !IvyRepository.isSnapshot(v.repr)
          availableVersions(module, fetch).flatMap {
            case None =>
              findNoInverval(module, version, fetch)
            case Some(v) =>
              fromVersions(filter, v)
          }
        } else
          findNoInverval(module, version, fetch)
      case Some(itv) =>
        availableVersions(module, fetch).flatMap {
          case None =>
            findNoInverval(module, version, fetch)
          case Some(v) =>
            fromVersions(itv.contains, v)
        }
    }
  }

  def findNoInverval[F[_]](
    module: Module,
    version: String,
    fetch: Repository.Fetch[F]
  )(implicit
    F: Monad[F]
  ): EitherT[F, String, (Artifact.Source, Project)] = {

    val eitherArtifact: Either[String, Artifact] =
      for {
        url <- metadataPattern.substituteVariables(
          variables(module, Some(version), Type.ivy, "ivy", Extension("xml"), None)
        ).right
      } yield {
        var artifact = artifactFor(
          url,
          changing = changing.getOrElse(IvyRepository.isSnapshot(version))
        )

        if (withChecksums)
          artifact = artifact.withDefaultChecksums
        if (withSignatures)
          artifact = artifact.withDefaultSignature

        artifact
      }

    for {
      artifact <- EitherT(F.point(eitherArtifact))
      ivy <- fetch(artifact)
      proj0 <- EitherT(
        F.point {
          for {
            xml <- compatibility.xmlParseDom(ivy).right
            _ <- (if (xml.label == "ivy-module") Right(()) else Left("Module definition not found")).right
            proj <- IvyXml.project(xml).right
          } yield proj
        }
      )
    } yield {
      val proj =
        if (dropInfoAttributes)
          proj0.copy(
            module = proj0.module.copy(
              attributes = proj0.module.attributes.filter {
                case (k, _) => !k.startsWith("info.")
              }
            ),
            dependencies = proj0.dependencies.map {
              case (config, dep0) =>
                val dep = dep0.copy(
                  module = dep0.module.copy(
                    attributes = dep0.module.attributes.filter {
                      case (k, _) => !k.startsWith("info.")
                    }
                  )
                )

                config -> dep
            }
          )
        else
          proj0

      this -> proj.copy(
        actualVersionOpt = Some(version)
      )
    }
  }

  override def completeOpt[F[_] : Monad](fetch: Fetch[F]): Some[Repository.Complete[F]] =
    Some(IvyComplete(this, fetch, Monad[F]))

}

object IvyRepository {

  def isSnapshot(version: String): Boolean =
    version.endsWith("SNAPSHOT")

  def parse(
    pattern: String,
    metadataPatternOpt: Option[String] = None,
    changing: Option[Boolean] = None,
    properties: Map[String, String] = Map.empty,
    withChecksums: Boolean = true,
    withSignatures: Boolean = true,
    withArtifacts: Boolean = true,
    // hack for sbt putting infos in properties
    dropInfoAttributes: Boolean = false,
    authentication: Option[Authentication] = None,
    substituteDefault: Boolean = true
  ): Either[String, IvyRepository] =

    for {
      propertiesPattern <- PropertiesPattern.parse(pattern).right
      metadataPropertiesPatternOpt <- metadataPatternOpt
        .fold[Either[String, Option[PropertiesPattern]]](Right(None))(PropertiesPattern.parse(_).right.map(Some(_)))
        .right

      pattern <- propertiesPattern.substituteProperties(properties).right
      metadataPatternOpt <- metadataPropertiesPatternOpt
        .fold[Either[String, Option[Pattern]]](Right(None))(_.substituteProperties(properties).right.map(Some(_)))
        .right

    } yield
      IvyRepository(
        if (substituteDefault) pattern.substituteDefault else pattern,
        metadataPatternOpt.map(p => if (substituteDefault) p.substituteDefault else p),
        changing,
        withChecksums,
        withSignatures,
        withArtifacts,
        dropInfoAttributes,
        authentication
      )

  // because of the compatibility apply method below, we can't give default values
  // to the default constructor of IvyPattern
  // this method accepts the same arguments as this constructor, with default values when possible
  def fromPattern(
    pattern: Pattern,
    metadataPatternOpt: Option[Pattern] = None,
    changing: Option[Boolean] = None,
    withChecksums: Boolean = true,
    withSignatures: Boolean = true,
    withArtifacts: Boolean = true,
    // hack for sbt putting infos in properties
    dropInfoAttributes: Boolean = false,
    authentication: Option[Authentication] = None
  ): IvyRepository =
    IvyRepository(
      pattern,
      metadataPatternOpt,
      changing,
      withChecksums,
      withSignatures,
      withArtifacts,
      dropInfoAttributes,
      authentication
    )
}
