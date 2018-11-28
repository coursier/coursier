package coursier.ivy

import coursier.Fetch
import coursier.core._
import coursier.util.{EitherT, Monad, WebPage}

final case class IvyRepository(
  pattern: Pattern,
  metadataPatternOpt: Option[Pattern],
  changing: Option[Boolean],
  withChecksums: Boolean,
  withSignatures: Boolean,
  withArtifacts: Boolean,
  // hack for SBT putting infos in properties
  dropInfoAttributes: Boolean,
  authentication: Option[Authentication]
) extends Repository {

  def metadataPattern: Pattern = metadataPatternOpt.getOrElse(pattern)

  lazy val revisionListingPatternOpt: Option[Pattern] = {
    val idx = metadataPattern.chunks.indexWhere { chunk =>
      chunk == Pattern.Chunk.Var("revision")
    }

    if (idx < 0)
      None
    else
      Some(Pattern(metadataPattern.chunks.take(idx)))
  }

  import Repository._

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
    Map(
      "organization" -> module.organization.value,
      "organisation" -> module.organization.value,
      "orgPath" -> module.organization.value.replace('.', '/'),
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
  ): Seq[(Attributes, Artifact)] =
    if (withArtifacts) {

      val retained =
        overrideClassifiers match {
          case None =>

            // FIXME Some duplication with what's done in MavenSource

            if (dependency.attributes.classifier.nonEmpty)
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

          var artifact = Artifact(
            url,
            Map.empty,
            Map.empty,
            changing = changing.getOrElse(IvyRepository.isSnapshot(project.version)),
            optional = false,
            authentication = authentication
          )

          if (withChecksums)
            artifact = artifact.withDefaultChecksums
          if (withSignatures)
            artifact = artifact.withDefaultSignature

          (p.attributes, artifact)
      }
    } else
      Nil

  def find[F[_]](
    module: Module,
    version: String,
    fetch: Fetch.Content[F]
  )(implicit
    F: Monad[F]
  ): EitherT[F, String, (Artifact.Source, Project)] = {

    revisionListingPatternOpt match {
      case None =>
        findNoInverval(module, version, fetch)
      case Some(revisionListingPattern) =>
        Parse.versionInterval(version)
          .orElse(Parse.multiVersionInterval(version))
          .orElse(Parse.ivyLatestSubRevisionInterval(version))
          .filter(_.isValid) match {
          case None =>
            findNoInverval(module, version, fetch)
          case Some(itv) =>
            val listingUrl = revisionListingPattern
              .substituteVariables(variables(module, None, Type.ivy, "ivy", Extension("xml"), None))
              .right
              .flatMap { s =>
                if (s.endsWith("/"))
                  Right(s)
                else
                  Left(s"Don't know how to list revisions of ${metadataPattern.string}")
              }

            def fromWebPage(url: String, s: String) = {

              val subDirs = WebPage.listDirectories(url, s)
              val versions = subDirs.map(Parse.version).collect { case Some(v) => v }
              val versionsInItv = versions.filter(itv.contains)

              if (versionsInItv.isEmpty)
                EitherT(
                  F.point[Either[String, (Artifact.Source, Project)]](Left(s"No version found for $version"))
                )
              else {
                val version0 = versionsInItv.max
                findNoInverval(module, version0.repr, fetch)
              }
            }

            def artifactFor(url: String) =
              Artifact(
                url,
                Map.empty,
                Map.empty,
                changing = changing.getOrElse(IvyRepository.isSnapshot(version)),
                optional = false,
                authentication
              )

            for {
              url <- EitherT(F.point(listingUrl))
              s <- fetch(artifactFor(url))
              res <- fromWebPage(url, s)
            } yield res
        }
    }
  }

  def findNoInverval[F[_]](
    module: Module,
    version: String,
    fetch: Fetch.Content[F]
  )(implicit
    F: Monad[F]
  ): EitherT[F, String, (Artifact.Source, Project)] = {

    val eitherArtifact: Either[String, Artifact] =
      for {
        url <- metadataPattern.substituteVariables(
          variables(module, Some(version), Type.ivy, "ivy", Extension("xml"), None)
        ).right
      } yield {
        var artifact = Artifact(
          url,
          Map.empty,
          Map.empty,
          changing = changing.getOrElse(IvyRepository.isSnapshot(version)),
          optional = false,
          authentication = authentication
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
            xml <- compatibility.xmlParse(ivy).right
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
    // hack for SBT putting infos in properties
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
    // hack for SBT putting infos in properties
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
