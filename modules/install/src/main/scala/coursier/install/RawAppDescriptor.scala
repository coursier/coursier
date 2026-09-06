package coursier.install

import dataclass.{data, since => unroll}

import cats.data.{NonEmptyList, Validated, ValidatedNel}
import cats.implicits._
import com.github.plokhotnyuk.jsoniter_scala.core._
import com.github.plokhotnyuk.jsoniter_scala.macros._
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
import scala.language.implicitConversions

@data case class RawAppDescriptor(
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
          .copy(
            repositories = repositories,
            dependencies =
              dependencies.map { dep =>
                dep.withUnderlyingDependency { dep0 =>
                  dep0.copy(
                    minimizedExclusions =
                      dep0.minimizedExclusions.join(MinimizedExclusions(exclusions.toSet))
                  )
                }
              },
            sharedDependencies = sharedDependencies,
            launcherType = launcherType,
            classifiers = classifiers0,
            mainArtifacts = mainArtifacts,
            artifactTypes = artifactTypes0,
            mainClass = mainClassOpt,
            defaultMainClass = defaultMainClassOpt,
            javaOptions = javaOptions,
            javaProperties = properties.props.sorted,
            scalaVersionOpt = scalaVersion,
            nameOpt = name,
            graalvmOptions = graalvm.map(_.graalvmOptions),
            prebuiltLauncher = prebuilt,
            jvmOptionFile = jvmOptionFile,
            prebuiltBinaries = prebuiltBinaries,
            jna = jna,
            versionOverrides = versionOverrides
          )
    }
  }
  def repr: String =
    Codecs.write(this)(RawAppDescriptor.codec)

  /** Same as [[repr]], with an indented / more human-readable output */
  def prettyRepr: String =
    Codecs.writeIndented(this)(RawAppDescriptor.codec)

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
          copy(
            dependencies = versionOverride.dependencies.getOrElse(dependencies),
            repositories = versionOverride.repositories.getOrElse(repositories),
            mainClass = versionOverride.mainClass.orElse(mainClass),
            properties = versionOverride.properties.getOrElse(properties)
          )
        }
      }
      else this
    base.overrideVersion(ver)
  }

  // version substitution possibly a bit flaky…
  def overrideVersion(ver: String): RawAppDescriptor =
    copy(
      dependencies =
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
    )

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
    implicit val codec: JsonValueCodec[Properties] =
      new JsonValueCodec[Properties] {
        def decodeValue(in: JsonReader, default: Properties): Properties =
          if (in.isNextToken('{')) {
            val b = List.newBuilder[(String, String)]
            if (!in.isNextToken('}')) {
              in.rollbackToken()
              while ({
                b += in.readKeyAsString() -> in.readString(null)
                in.isNextToken(',')
              }) ()
              if (!in.isCurrentToken('}'))
                in.objectEndOrCommaError()
            }
            Properties(b.result())
          }
          else
            in.decodeError("expected JSON object")
        def encodeValue(x: Properties, out: JsonWriter): Unit = {
          out.writeObjectStart()
          for ((k, v) <- x.props) {
            out.writeKey(k)
            out.writeVal(v)
          }
          out.writeObjectEnd()
        }
        def nullValue: Properties =
          null.asInstanceOf[Properties]
      }
  }

  @data case class RawGraalvmOptions(
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

    private final case class RawGraalvmOptionsJson(
      options: List[String] = Nil
    ) {
      def get: RawGraalvmOptions =
        RawGraalvmOptions()
          .copy(options = options)
    }

    private def optionsJson(opt: RawGraalvmOptions): RawGraalvmOptionsJson =
      RawGraalvmOptionsJson(opt.options)

    private val jsonCodec: JsonValueCodec[RawGraalvmOptionsJson] =
      JsonCodecMaker.make

    implicit val codec: JsonValueCodec[RawGraalvmOptions] =
      new JsonValueCodec[RawGraalvmOptions] {
        def decodeValue(in: JsonReader, default: RawGraalvmOptions): RawGraalvmOptions =
          jsonCodec.decodeValue(in, jsonCodec.nullValue).get
        def encodeValue(x: RawGraalvmOptions, out: JsonWriter): Unit =
          jsonCodec.encodeValue(optionsJson(x), out)
        def nullValue: RawGraalvmOptions =
          null
      }

  }

  @data case class RawVersionOverride(
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
            .copy(
              dependencies = dependencies,
              repositories = repositories,
              mainClass = mainClassOpt,
              defaultMainClass = defaultMainClassOpt,
              javaProperties = properties.map(_.props.sorted),
              prebuiltLauncher = prebuilt,
              prebuiltBinaries = prebuiltBinaries,
              launcherType = launcherType
            )
      }
    }
  }

  object RawVersionOverride {

    private final case class RawVersionOverrideJson(
      versionRange: String,
      dependencies: Option[List[String]] = None,
      repositories: Option[List[String]] = None,
      mainClass: Option[String] = None,
      properties: Option[Properties] = None,
      prebuilt: Option[String] = None,
      prebuiltBinaries: Option[Map[String, String]] = None,
      launcherType: Option[String] = None
    ) {
      def get: RawVersionOverride =
        RawVersionOverride(versionRange)
          .copy(
            dependencies = dependencies,
            repositories = repositories,
            mainClass = mainClass,
            properties = properties,
            prebuilt = prebuilt,
            prebuiltBinaries = prebuiltBinaries,
            launcherType = launcherType
          )
    }

    private def overrideJson(o: RawVersionOverride): RawVersionOverrideJson =
      RawVersionOverrideJson(
        versionRange = o.versionRange,
        dependencies = o.dependencies,
        repositories = o.repositories,
        mainClass = o.mainClass,
        properties = o.properties,
        prebuilt = o.prebuilt,
        prebuiltBinaries = o.prebuiltBinaries,
        launcherType = o.launcherType
      )

    // all fields are always written out, absent ones as null, like the former
    // argonaut-shapeless-derived codec used to do
    private val jsonCodec: JsonValueCodec[RawVersionOverrideJson] =
      JsonCodecMaker.make(
        CodecMakerConfig
          .withTransientDefault(false)
          .withTransientEmpty(false)
          .withTransientNone(false)
      )

    implicit val codec: JsonValueCodec[RawVersionOverride] =
      new JsonValueCodec[RawVersionOverride] {
        def decodeValue(in: JsonReader, default: RawVersionOverride): RawVersionOverride =
          jsonCodec.decodeValue(in, jsonCodec.nullValue).get
        def encodeValue(x: RawVersionOverride, out: JsonWriter): Unit =
          jsonCodec.encodeValue(overrideJson(x), out)
        def nullValue: RawVersionOverride =
          null
      }

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
        .copy(
          repositories = repositories,
          shared = shared,
          exclusions = exclusions,
          classifiers = classifiers,
          artifactTypes = artifactTypes,
          mainClass = mainClass,
          javaOptions = javaOptions,
          scalaVersion = scalaVersion,
          name = name,
          graalvm = graalvm,
          prebuilt = prebuilt,
          jvmOptionFile = jvmOptionFile,
          prebuiltBinaries = prebuiltBinaries,
          jna = jna,
          versionOverrides = versionOverrides
        )
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

  private val jsonCodec: JsonValueCodec[RawAppDescriptorJson] =
    JsonCodecMaker.make

  implicit val codec: JsonValueCodec[RawAppDescriptor] =
    new JsonValueCodec[RawAppDescriptor] {
      def decodeValue(in: JsonReader, default: RawAppDescriptor): RawAppDescriptor =
        jsonCodec.decodeValue(in, jsonCodec.nullValue).get
      def encodeValue(x: RawAppDescriptor, out: JsonWriter): Unit =
        jsonCodec.encodeValue(descriptorJson(x), out)
      def nullValue: RawAppDescriptor =
        null
    }

  def parse(input: String): Either[String, RawAppDescriptor] =
    Codecs.read(input)(codec)

}
