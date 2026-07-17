package coursier.install

import argonaut._
import cats.data.{NonEmptyList, Validated, ValidatedNel}
import cats.implicits._
import coursier.core.{
  Classifier,
  Configuration,
  ModuleName,
  Resolution,
  Type,
  Repository,
  MinimizedExclusions
}
import coursier.parse.{
  DependencyParser,
  JavaOrScalaModule,
  ModuleParser,
  RepositoryParser,
  JavaOrScalaDependency
}
import coursier.version.{VersionInterval, VersionParse}
import scala.annotation.unroll

import scala.language.implicitConversions

final case class RawAppDescriptor(
  dependencies: List[String],
  repositories: List[String] = Nil,
  shared: List[String] = Nil,
  exclusions: List[String] = Nil,
  launcherType: String = "bootstrap",
  classifiers: List[String] = Nil,
  artifactTypes: List[String] = Nil,
  mainClass: Option[String] = None,
  javaOptions: List[String] = Nil,
  properties: RawAppDescriptor.Properties = RawAppDescriptor.Properties(Nil),
  scalaVersion: Option[String] = None,
  name: Option[String] = None,
  graalvm: Option[RawAppDescriptor.RawGraalvmOptions] = None,
  @unroll
  prebuilt: Option[String] = None,
  @unroll
  jvmOptionFile: Option[String] = None,
  @unroll
  prebuiltBinaries: Map[String, String] = Map.empty,
  @unroll
  jna: List[String] = Nil,
  @unroll
  versionOverrides: List[RawAppDescriptor.RawVersionOverride] = Nil
) {
  def isEmpty: Boolean =
    this == RawAppDescriptor(Nil)
  def appDescriptor: ValidatedNel[String, AppDescriptor] = {

    import RawAppDescriptor._

    val repositoriesV       = parseRepositories(repositories)
    val dependenciesV       = parseDependenices(dependencies)
    val sharedDependenciesV = validationNelToCats(ModuleParser.javaOrScalaModules(shared))

    val exclusionsV = validationNelToCats(ModuleParser.javaOrScalaModules(exclusions)).map(_.map {
      case j: JavaOrScalaModule.JavaModule =>
        (j.module.organization, j.module.name)
      case s: JavaOrScalaModule.ScalaModule =>
        // FIXME We're changing exclusions like 'org::foo' or 'org:::foo' to 'org:foo_*' here
        (s.baseModule.organization, ModuleName(s.baseModule.name.value + "_*"))
    })

    val launcherTypeV: ValidatedNel[String, LauncherType] =
      Validated.fromEither(LauncherType.parse(launcherType).left.map(NonEmptyList.one))

    val (mainArtifacts, classifiers0) = {
      val classifiers0 = classifiers
        .flatMap(_.split(','))
        .filter(_.nonEmpty)
        .map(Classifier(_))
        .toSet

      if (classifiers0.isEmpty || classifiers0(Classifier("_")))
        (true, classifiers0 - Classifier("_"))
      else
        (false, classifiers0)
    }

    val artifactTypes0 = {
      val types0 = artifactTypes
        .flatMap(_.split(',').toSeq)
        .filter(_.nonEmpty)
        .map(Type(_))
        .toSet

      if (types0(Type.all))
        Set(Type.all)
      else {
        val default0 = types0.isEmpty || types0(Type("_"))
        val defaultTypes =
          if (default0) {
            val sourceTypes  = Some(Type.source).filter(_ => classifiers0(Classifier.sources)).toSet
            val javadocTypes = Some(Type.doc).filter(_ => classifiers0(Classifier.javadoc)).toSet
            Resolution.defaultTypes ++ sourceTypes ++ javadocTypes
          }
          else
            Set()

        (defaultTypes ++ types0) - Type("_")
      }
    }

    val (mainClassOpt, defaultMainClassOpt) = mainClass.map(parseMainClass) match {
      case Some(Left(mainClass))         => (Some(mainClass), None)
      case Some(Right(defaultMainClass)) => (None, Some(defaultMainClass))
      case None                          => (None, None)
    }

    val versionOverridesV =
      versionOverrides.map(_.versionOverride).sequence.andThen(validateRanges)

    (
      repositoriesV,
      dependenciesV,
      sharedDependenciesV,
      exclusionsV,
      launcherTypeV,
      versionOverridesV
    ).mapN {
      (
        repositories,
        dependencies,
        sharedDependencies,
        exclusions,
        launcherType,
        versionOverrides
      ) =>
        AppDescriptor()
          .copy(repositories = repositories)
          .copy(dependencies = {
            dependencies.map { dep =>
              dep.withUnderlyingDependency { dep0 =>
                dep0.copy(minimizedExclusions =
                  dep0.minimizedExclusions.join(MinimizedExclusions(exclusions.toSet))
                )
              }
            }
          })
          .copy(sharedDependencies = sharedDependencies)
          .copy(launcherType = launcherType)
          .copy(classifiers = classifiers0)
          .copy(mainArtifacts = mainArtifacts)
          .copy(artifactTypes = artifactTypes0)
          .copy(mainClass = mainClassOpt)
          .copy(defaultMainClass = defaultMainClassOpt)
          .copy(javaOptions = javaOptions)
          .copy(javaProperties = properties.props.sorted)
          .copy(scalaVersionOpt = scalaVersion)
          .copy(nameOpt = name)
          .copy(graalvmOptions = graalvm.map(_.graalvmOptions))
          .copy(prebuiltLauncher = prebuilt)
          .copy(jvmOptionFile = jvmOptionFile)
          .copy(prebuiltBinaries = prebuiltBinaries)
          .copy(jna = jna)
          .copy(versionOverrides = versionOverrides)
    }
  }
  def repr: String =
    RawAppDescriptor.encoder.encode(this).nospaces

  def overrideVersion(ver: String, useVersionOverrides: Boolean): RawAppDescriptor = {
    val base =
      if (useVersionOverrides) {
        val ver0 = coursier.version.Version(ver)
        val versionOverrideOpt = versionOverrides
          .iterator
          .flatMap { o =>
            o.versionOverride.toEither match {
              case Left(errors) =>
                // FIXME Log errors
                Iterator.empty
              case Right(ov) if ov.versionRange0.contains(ver0) =>
                Iterator(o)
              case Right(_) =>
                Iterator.empty
            }
          }
          .find(_ => true)
        versionOverrideOpt.fold(this) { versionOverride =>
          copy(dependencies = versionOverride.dependencies.getOrElse(dependencies))
            .copy(repositories = versionOverride.repositories.getOrElse(repositories))
            .copy(mainClass = versionOverride.mainClass.orElse(mainClass))
            .copy(properties = versionOverride.properties.getOrElse(properties))
        }
      }
      else this
    base.overrideVersion(ver)
  }

  // version substitution possibly a bit flaky…
  def overrideVersion(ver: String): RawAppDescriptor =
    copy(dependencies = {
      if (dependencies.isEmpty)
        dependencies
      else {
        val dep = {
          val dep0 = dependencies.head
          val idx  = dep0.lastIndexOf(':')
          if (idx < 0)
            dep0 // ???
          else
            dep0.take(idx + 1) + ver
        }
        dep +: dependencies.tail
      }
    })

  def overrideVersion(verOpt: Option[String]): RawAppDescriptor =
    verOpt.fold(this)(overrideVersion(_))

  def overrideVersion(verOpt: Option[String], useVersionOverrides: Boolean): RawAppDescriptor =
    verOpt.fold(this)(overrideVersion(_, useVersionOverrides))
}

object RawAppDescriptor {

  final case class Properties(props: Seq[(String, String)]) extends AnyVal

  object Properties {
    implicit def fromSeq(s: Seq[(String, String)]): Properties =
      Properties(s)
    implicit val encoder: EncodeJson[Properties] =
      EncodeJson { props =>
        Json.obj(props.props.map { case (k, v) => k -> Json.jString(v) }: _*)
      }
    implicit val decoder: DecodeJson[Properties] =
      DecodeJson { c =>
        c.focus.obj match {
          case None => DecodeResult.fail("Expected JSON object", c.history)
          case Some(obj) =>
            obj
              .toList
              .foldLeft(DecodeResult.ok(List.empty[(String, String)])) {
                case (acc, (k, v)) =>
                  for (a <- acc; s <- v.as[String]) yield (k -> s) :: a
              }
              .map(l => Properties(l.reverse))
        }
      }
  }

  import argonaut.Argonaut._

  final case class RawGraalvmOptions(
    options: List[String] = Nil,
    version: Option[String] = None
  ) {
    def graalvmOptions: AppDescriptor.GraalvmOptions =
      AppDescriptor.GraalvmOptions(
        version.filter(_.nonEmpty),
        options
      )
  }

  object RawGraalvmOptions {

    // Only the `options` field is serialized (matching the former argonaut-shapeless derivation
    // over RawGraalvmOptionsJson).
    implicit val encoder: EncodeJson[RawGraalvmOptions] =
      EncodeJson { opt =>
        Json.obj("options" := opt.options)
      }
    implicit val decoder: DecodeJson[RawGraalvmOptions] =
      DecodeJson { c =>
        (c --\ "options").as[Option[List[String]]].map(o => RawGraalvmOptions().copy(options = o.getOrElse(Nil)))
      }

  }

  final case class RawVersionOverride(
    versionRange: String,
    dependencies: Option[List[String]] = None,
    repositories: Option[List[String]] = None,
    mainClass: Option[String] = None,
    properties: Option[RawAppDescriptor.Properties] = None,
    @unroll
    prebuilt: Option[String] = None,
    prebuiltBinaries: Option[Map[String, String]] = None,
    @unroll
    launcherType: Option[String] = None
  ) {
    def versionOverride: ValidatedNel[String, VersionOverride] = {
      val versionRangeV = VersionParse.versionInterval(versionRange)
        .toValidNel(s"""versionRange "$versionRange" is invalid""")
      val repositoriesV = repositories.map(parseRepositories).sequence
      val dependenciesV = dependencies.map(parseDependenices).sequence
      val (mainClassOpt, defaultMainClassOpt) = mainClass.map(parseMainClass) match {
        case Some(Left(mainClass))         => (Some(mainClass), Some(""))
        case Some(Right(defaultMainClass)) => (Some(""), Some(defaultMainClass))
        case None                          => (None, None)
      }

      val launcherTypeV: ValidatedNel[String, Option[LauncherType]] =
        launcherType.map(lt =>
          Validated.fromEither(LauncherType.parse(lt).left.map(NonEmptyList.one))
        ).sequence

      (versionRangeV, repositoriesV, dependenciesV, launcherTypeV).mapN {
        (versionRange, repositories, dependencies, launcherType) =>
          VersionOverride(versionRange)
            .copy(dependencies = dependencies)
            .copy(repositories = repositories)
            .copy(mainClass = mainClassOpt)
            .copy(defaultMainClass = defaultMainClassOpt)
            .copy(javaProperties = properties.map(_.props.sorted))
            .copy(prebuiltLauncher = prebuilt)
            .copy(prebuiltBinaries = prebuiltBinaries)
            .copy(launcherType = launcherType)
      }
    }
  }

  object RawVersionOverride {
    import argonaut.Argonaut._
    implicit val codec: argonaut.CodecJson[RawVersionOverride] =
      casecodec8(
        (
          versionRange: String,
          dependencies: Option[List[String]],
          repositories: Option[List[String]],
          mainClass: Option[String],
          properties: Option[RawAppDescriptor.Properties],
          prebuilt: Option[String],
          prebuiltBinaries: Option[Map[String, String]],
          launcherType: Option[String]
        ) =>
          RawVersionOverride(
            versionRange,
            dependencies,
            repositories,
            mainClass,
            properties,
            prebuilt,
            prebuiltBinaries,
            launcherType
          ),
        (v: RawVersionOverride) =>
          Some(
            (
              v.versionRange,
              v.dependencies,
              v.repositories,
              v.mainClass,
              v.properties,
              v.prebuilt,
              v.prebuiltBinaries,
              v.launcherType
            )
          )
      )(
        "versionRange",
        "dependencies",
        "repositories",
        "mainClass",
        "properties",
        "prebuilt",
        "prebuiltBinaries",
        "launcherType"
      )
  }

  /* Left is mainClass and Right is defaultMainClass */
  private def parseMainClass(mainClass: String): Either[String, String] =
    if (mainClass.endsWith("?")) Right(mainClass.stripSuffix("?"))
    else Left(mainClass)

  private def parseDependenices(dependencies: Seq[String])
    : ValidatedNel[String, Seq[JavaOrScalaDependency]] =
    validationNelToCats(
      DependencyParser.javaOrScalaDependencies(dependencies, Configuration.defaultRuntime)
    )

  private def parseRepositories(repositories: Seq[String]): ValidatedNel[String, Seq[Repository]] =
    validationNelToCats(RepositoryParser.repositories(repositories))

  /** Check that there is no overlapping between version intervals
    */
  private[install] def validateRanges(versionOverrides: Seq[VersionOverride])
    : ValidatedNel[String, Seq[VersionOverride]] =
    versionOverrides
      .map(_.versionRange0)
      .foldLeft[ValidatedNel[String, Seq[VersionInterval]]](Validated.valid(Seq.empty)) {
        case (validRanges, range) =>
          validRanges.andThen { ranges =>
            val conflictingRanges = ranges.filter(_.merge(range).nonEmpty)

            if (conflictingRanges.isEmpty) Validated.valid(ranges :+ range)
            else {
              val conflicts = conflictingRanges.map(i => "\"" + i + "\"").mkString("[", ", ", "]")
              Validated.invalidNel(s"""versionRange "$range" conflicts with $conflicts""")
            }
          }
      }
      .map(_ => versionOverrides)

  private[install] implicit def validationNelToCats[L, R](
    v: coursier.util.ValidationNel[L, R]
  ): ValidatedNel[L, R] =
    v.either match {
      case Left(h :: t) => Validated.invalid(NonEmptyList.of(h, t: _*))
      case Right(r)     => Validated.validNel(r)
    }

  private final case class RawAppDescriptorJson(
    dependencies: List[String] = Nil,
    repositories: List[String] = Nil,
    shared: List[String] = Nil,
    exclusions: List[String] = Nil,
    launcherType: Option[String] = None,
    classifiers: List[String] = Nil,
    artifactTypes: List[String] = Nil,
    mainClass: Option[String] = None,
    javaOptions: List[String] = Nil,
    properties: Option[RawAppDescriptor.Properties] = None,
    scalaVersion: Option[String] = None,
    name: Option[String] = None,
    graalvm: Option[RawAppDescriptor.RawGraalvmOptions] = None,
    prebuilt: Option[String] = None,
    jvmOptionFile: Option[String] = None,
    prebuiltBinaries: Map[String, String] = Map.empty,
    jna: List[String] = Nil,
    versionOverrides: List[RawVersionOverride] = Nil
  ) {
    def get: RawAppDescriptor = {
      var d = RawAppDescriptor(dependencies)
        .copy(repositories = repositories)
        .copy(shared = shared)
        .copy(exclusions = exclusions)
        .copy(classifiers = classifiers)
        .copy(artifactTypes = artifactTypes)
        .copy(mainClass = mainClass)
        .copy(javaOptions = javaOptions)
        .copy(scalaVersion = scalaVersion)
        .copy(name = name)
        .copy(graalvm = graalvm)
        .copy(prebuilt = prebuilt)
        .copy(jvmOptionFile = jvmOptionFile)
        .copy(prebuiltBinaries = prebuiltBinaries)
        .copy(jna = jna)
        .copy(versionOverrides = versionOverrides)
      for (t <- launcherType)
        d = d.copy(launcherType = t)
      for (p <- properties)
        d = d.copy(properties = p)
      d
    }
  }

  private def descriptorJson(desc: RawAppDescriptor): RawAppDescriptorJson =
    RawAppDescriptorJson(
      dependencies = desc.dependencies,
      repositories = desc.repositories,
      shared = desc.shared,
      exclusions = desc.exclusions,
      launcherType = Some(desc.launcherType),
      classifiers = desc.classifiers,
      artifactTypes = desc.artifactTypes,
      mainClass = desc.mainClass,
      javaOptions = desc.javaOptions,
      properties = Some(desc.properties),
      scalaVersion = desc.scalaVersion,
      name = desc.name,
      graalvm = desc.graalvm,
      prebuilt = desc.prebuilt,
      jvmOptionFile = desc.jvmOptionFile,
      prebuiltBinaries = desc.prebuiltBinaries,
      jna = desc.jna,
      versionOverrides = desc.versionOverrides
    )

  implicit val encoder: EncodeJson[RawAppDescriptor] =
    EncodeJson { desc =>
      val j = descriptorJson(desc)
      Json.obj(
        "dependencies"     := j.dependencies,
        "repositories"     := j.repositories,
        "shared"           := j.shared,
        "exclusions"       := j.exclusions,
        "launcherType"     := j.launcherType,
        "classifiers"      := j.classifiers,
        "artifactTypes"    := j.artifactTypes,
        "mainClass"        := j.mainClass,
        "javaOptions"      := j.javaOptions,
        "properties"       := j.properties,
        "scalaVersion"     := j.scalaVersion,
        "name"             := j.name,
        "graalvm"          := j.graalvm,
        "prebuilt"         := j.prebuilt,
        "jvmOptionFile"    := j.jvmOptionFile,
        "prebuiltBinaries" := j.prebuiltBinaries,
        "jna"              := j.jna,
        "versionOverrides" := j.versionOverrides,
        "prebuiltFallback" := j.prebuiltFallback
      )
    }
  implicit val decoder: DecodeJson[RawAppDescriptor] =
    DecodeJson { c =>
      for {
        dependencies     <- (c --\ "dependencies").as[Option[List[String]]]
        repositories     <- (c --\ "repositories").as[Option[List[String]]]
        shared           <- (c --\ "shared").as[Option[List[String]]]
        exclusions       <- (c --\ "exclusions").as[Option[List[String]]]
        launcherType     <- (c --\ "launcherType").as[Option[String]]
        classifiers      <- (c --\ "classifiers").as[Option[List[String]]]
        artifactTypes    <- (c --\ "artifactTypes").as[Option[List[String]]]
        mainClass        <- (c --\ "mainClass").as[Option[String]]
        javaOptions      <- (c --\ "javaOptions").as[Option[List[String]]]
        properties       <- (c --\ "properties").as[Option[RawAppDescriptor.Properties]]
        scalaVersion     <- (c --\ "scalaVersion").as[Option[String]]
        name             <- (c --\ "name").as[Option[String]]
        graalvm          <- (c --\ "graalvm").as[Option[RawAppDescriptor.RawGraalvmOptions]]
        prebuilt         <- (c --\ "prebuilt").as[Option[String]]
        jvmOptionFile    <- (c --\ "jvmOptionFile").as[Option[String]]
        prebuiltBinaries <- (c --\ "prebuiltBinaries").as[Option[Map[String, String]]]
        jna              <- (c --\ "jna").as[Option[List[String]]]
        versionOverrides <- (c --\ "versionOverrides").as[Option[List[RawVersionOverride]]]
        prebuiltFallback <- (c --\ "prebuiltFallback").as[Option[String]]
      } yield RawAppDescriptorJson(
        dependencies.getOrElse(Nil),
        repositories.getOrElse(Nil),
        shared.getOrElse(Nil),
        exclusions.getOrElse(Nil),
        launcherType,
        classifiers.getOrElse(Nil),
        artifactTypes.getOrElse(Nil),
        mainClass,
        javaOptions.getOrElse(Nil),
        properties,
        scalaVersion,
        name,
        graalvm,
        prebuilt,
        jvmOptionFile,
        prebuiltBinaries.getOrElse(Map.empty),
        jna.getOrElse(Nil),
        versionOverrides.getOrElse(Nil),
        prebuiltFallback
      ).get
    }

  def parse(input: String): Either[String, RawAppDescriptor] =
    Parse.decodeEither(input)(using decoder)

}
