package coursier.ivy

import coursier.core._
import coursier.maven.{MavenAttributes, MavenComplete}
import coursier.util.{Artifact, EitherT, Monad}

final class IvyRepository private (
  val pattern: Pattern,
  val metadataPatternOpt: Option[Pattern],
  val changing: Option[Boolean],
  val withChecksums: Boolean,
  val withSignatures: Boolean,
  val withArtifacts: Boolean,
  // hack for sbt putting infos in properties
  val dropInfoAttributes: Boolean,
  val authentication: Option[Authentication],
  override val versionsCheckHasModule: Boolean
) extends Repository {

  override def equals(obj: Any): Boolean =
    obj match {
      case other: IvyRepository =>
        pattern == other.pattern &&
          metadataPatternOpt == other.metadataPatternOpt &&
          changing == other.changing &&
          withChecksums == other.withChecksums &&
          withSignatures == other.withSignatures &&
          withArtifacts == other.withArtifacts &&
          dropInfoAttributes == other.dropInfoAttributes &&
          authentication == other.authentication &&
          versionsCheckHasModule == other.versionsCheckHasModule
      case _ => false
    }

  override def hashCode(): Int = {
    var code = 17 + "coursier.ivy.IvyRepository".##
    code = 37 * code + pattern.##
    code = 37 * code + metadataPatternOpt.##
    code = 37 * code + changing.##
    code = 37 * code + withChecksums.##
    code = 37 * code + withSignatures.##
    code = 37 * code + withArtifacts.##
    code = 37 * code + dropInfoAttributes.##
    code = 37 * code + authentication.##
    code = 37 * code + versionsCheckHasModule.##
    37 * code
  }

  override def toString: String =
    s"IvyRepository($pattern, $metadataPatternOpt, $changing, $withChecksums, $withSignatures, $withArtifacts, $dropInfoAttributes, $authentication, $versionsCheckHasModule)"

  private def copy0(
    pattern: Pattern = pattern,
    metadataPatternOpt: Option[Pattern] = metadataPatternOpt,
    changing: Option[Boolean] = changing,
    withChecksums: Boolean = withChecksums,
    withSignatures: Boolean = withSignatures,
    withArtifacts: Boolean = withArtifacts,
    dropInfoAttributes: Boolean = dropInfoAttributes,
    authentication: Option[Authentication] = authentication,
    versionsCheckHasModule: Boolean = versionsCheckHasModule
  ): IvyRepository =
    new IvyRepository(
      pattern,
      metadataPatternOpt,
      changing,
      withChecksums,
      withSignatures,
      withArtifacts,
      dropInfoAttributes,
      authentication,
      versionsCheckHasModule
    )

  @deprecated("Use the with* methods instead", "2.0.0-RC3")
  def copy(
    pattern: Pattern = pattern,
    metadataPatternOpt: Option[Pattern] = metadataPatternOpt,
    changing: Option[Boolean] = changing,
    withChecksums: Boolean = withChecksums,
    withSignatures: Boolean = withSignatures,
    withArtifacts: Boolean = withArtifacts,
    dropInfoAttributes: Boolean = dropInfoAttributes,
    authentication: Option[Authentication] = authentication
  ): IvyRepository =
    copy0(
      pattern,
      metadataPatternOpt,
      changing,
      withChecksums,
      withSignatures,
      withArtifacts,
      dropInfoAttributes,
      authentication
    )

  def withPattern(pattern: Pattern): IvyRepository =
    copy0(pattern = pattern)
  def withMetadataPattern(metadataPatternOpt: Option[Pattern]): IvyRepository =
    copy0(metadataPatternOpt = metadataPatternOpt)
  def withMetadataPattern(metadataPattern: Pattern): IvyRepository =
    copy0(metadataPatternOpt = Some(metadataPattern))
  def withChanging(changingOpt: Option[Boolean]): IvyRepository =
    copy0(changing = changingOpt)
  def withChanging(changing: Boolean): IvyRepository =
    copy0(changing = Some(changing))
  def withWithChecksums(withChecksums: Boolean): IvyRepository =
    copy0(withChecksums = withChecksums)
  def withWithSignatures(withSignatures: Boolean): IvyRepository =
    copy0(withSignatures = withSignatures)
  def withWithArtifacts(withArtifacts: Boolean): IvyRepository =
    copy0(withArtifacts = withArtifacts)
  def withDropInfoAttributes(dropInfoAttributes: Boolean): IvyRepository =
    copy0(dropInfoAttributes = dropInfoAttributes)
  def withAuthentication(authentication: Option[Authentication]): IvyRepository =
    copy0(authentication = authentication)
  def withVersionsCheckHasModule(versionsCheckHasModule: Boolean): IvyRepository =
    copy0(versionsCheckHasModule = versionsCheckHasModule)


  override def repr: String =
    "ivy:" + pattern.string + metadataPatternOpt.fold("")("|" + _.string)

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

  private def artifactFor(url: String, changing: Boolean, cacheErrors: Boolean = false) =
    Artifact(
      url,
      Map.empty,
      if (cacheErrors)
        Map("cache-errors" -> Artifact("", Map.empty, Map.empty, changing = false, optional = false, None))
      else
        Map.empty,
      changing = changing,
      optional = false,
      authentication
    )

  private[ivy] def listing[F[_]](
    listingPatternOpt: Option[Pattern],
    listingName: String,
    variables: Map[String, String],
    fetch: Repository.Fetch[F],
    prefix: String
  )(implicit
    F: Monad[F]
  ): EitherT[F, String, Option[(String, Seq[String])]] =
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
          s <- fetch(artifactFor(url + ".links", changing = true, cacheErrors = true))
        } yield Some((url, MavenComplete.split0(s, '\n', prefix)))
    }

  private[ivy] def availableVersions[F[_]](
    module: Module,
    fetch: Repository.Fetch[F],
    prefix: String
  )(implicit
    F: Monad[F]
  ): EitherT[F, String, Option[(String, Seq[Version])]] =
    listing(
      revisionListingPatternOpt,
      "revisions",
      variables(module, None, Type.ivy, "ivy", Extension("xml"), None),
      fetch,
      prefix
    ).map(_.map(t => t._1 -> t._2.map(Parse.version).collect { case Some(v) => v }))

  override protected def fetchVersions[F[_]](
    module: Module,
    fetch: Repository.Fetch[F]
  )(implicit
    F: Monad[F]
  ): EitherT[F, String, (Versions, String)] =
    availableVersions(module, fetch, "").map {
      case Some((listingUrl, l)) if l.nonEmpty =>
        val latest = l.max.repr
        val release = {
          val l0 = l.filter(!_.repr.endsWith("SNAPSHOT"))
          if (l0.isEmpty)
            ""
          else
            l0.max.repr
        }
        val v = Versions(
          latest,
          release,
          l.map(_.repr).toList,
          None
        )
        (v, listingUrl)
      case Some((listingUrl, _)) =>
        (Versions.empty, listingUrl)
      case None =>
        (Versions.empty, "")
    }

  def find[F[_]](
    module: Module,
    version: String,
    fetch: Repository.Fetch[F]
  )(implicit
    F: Monad[F]
  ): EitherT[F, String, (ArtifactSource, Project)] = {

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
          proj0
            .withModule(
              proj0.module.withAttributes(
                proj0.module.attributes.filter {
                  case (k, _) => !k.startsWith("info.")
                }
              )
            )
            .withDependencies(
              proj0.dependencies.map {
                case (config, dep0) =>
                  val dep = dep0.copy(
                    module = dep0.module.withAttributes(
                      dep0.module.attributes.filter {
                        case (k, _) => !k.startsWith("info.")
                      }
                    )
                  )

                  config -> dep
              }
            )
        else
          proj0

      this -> proj.withActualVersionOpt(Some(version))
    }
  }

  override def completeOpt[F[_] : Monad](fetch: Fetch[F]): Some[Repository.Complete[F]] =
    Some(IvyComplete(this, fetch, Monad[F]))

}

object IvyRepository {

  def apply(
    pattern: Pattern,
    metadataPatternOpt: Option[Pattern],
    changing: Option[Boolean],
    withChecksums: Boolean,
    withSignatures: Boolean,
    withArtifacts: Boolean,
    dropInfoAttributes: Boolean,
    authentication: Option[Authentication]
  ): IvyRepository =
    new IvyRepository(
      pattern,
      metadataPatternOpt,
      changing,
      withChecksums,
      withSignatures,
      withArtifacts,
      dropInfoAttributes,
      authentication,
      versionsCheckHasModule = true
    )

  def apply(pattern: Pattern): IvyRepository =
    new IvyRepository(
      pattern,
      metadataPatternOpt = None,
      changing = None,
      withChecksums = true,
      withSignatures = true,
      withArtifacts = true,
      dropInfoAttributes = false,
      None,
      versionsCheckHasModule = true
    )

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
