package coursier.install

import argonaut._
import cats.data.{NonEmptyList, Validated, ValidatedNel}
import cats.implicits._
import coursier.core.{Classifier, Configuration, ModuleName, Resolution, Type}
import coursier.parse.{DependencyParser, JavaOrScalaModule, ModuleParser, RepositoryParser}
import dataclass._

import scala.language.implicitConversions

@data class RawAppDescriptor(
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
  @since
  prebuilt: Option[String] = None
) {
  def isEmpty: Boolean =
    this == RawAppDescriptor(Nil)
  def appDescriptor: ValidatedNel[String, AppDescriptor] = {

    import RawAppDescriptor.validationNelToCats

    val repositoriesV = validationNelToCats(RepositoryParser.repositories(repositories))

    val dependenciesV = validationNelToCats(
      DependencyParser.javaOrScalaDependencies(dependencies, Configuration.defaultCompile)
    )
    val sharedDependenciesV = validationNelToCats(ModuleParser.javaOrScalaModules(shared))

    val exclusionsV = validationNelToCats(ModuleParser.javaOrScalaModules(exclusions)).map(_.map {
      case j: JavaOrScalaModule.JavaModule =>
        (j.module.organization, j.module.name)
      case s: JavaOrScalaModule.ScalaModule =>
        // FIXME We're changing exclusions like 'org::foo' or 'org:::foo' to 'org:foo_*' here
        (s.baseModule.organization, ModuleName(s.baseModule.name.value + "_*"))
    })

    val launcherTypeV: ValidatedNel[String, LauncherType] = Validated.fromEither(LauncherType.parse(launcherType).left.map(NonEmptyList.one))

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
            val sourceTypes = Some(Type.source).filter(_ => classifiers0(Classifier.sources)).toSet
            val javadocTypes = Some(Type.doc).filter(_ => classifiers0(Classifier.javadoc)).toSet
            Resolution.defaultTypes ++ sourceTypes ++ javadocTypes
          } else
            Set()

        (defaultTypes ++ types0) - Type("_")
      }
    }

    val (mainClassOpt, defaultMainClassOpt) =
      mainClass match {
        case Some(c) if c.endsWith("?") => (None, Some(c.stripSuffix("?")))
        case Some(c) => (Some(c), None)
        case None => (None, None)
      }

    (repositoriesV, dependenciesV, sharedDependenciesV, exclusionsV, launcherTypeV).mapN {
      (repositories, dependencies, sharedDependencies, exclusions, launcherType) =>
        AppDescriptor()
          .withRepositories(repositories)
          .withDependencies {
            dependencies.map { dep =>
              dep.withUnderlyingDependency { dep0 =>
                dep0.withExclusions(dep0.exclusions ++ exclusions)
              }
            }
          }
          .withSharedDependencies(sharedDependencies)
          .withLauncherType(launcherType)
          .withClassifiers(classifiers0)
          .withMainArtifacts(mainArtifacts)
          .withArtifactTypes(artifactTypes0)
          .withMainClass(mainClassOpt)
          .withDefaultMainClass(defaultMainClassOpt)
          .withJavaOptions(javaOptions)
          .withJavaProperties(properties.props.sorted)
          .withScalaVersionOpt(scalaVersion)
          .withNameOpt(name)
          .withGraalvmOptions(graalvm.map(_.graalvmOptions))
          .withPrebuiltLauncher(prebuilt)
    }
  }
  def repr: String =
    RawAppDescriptor.encoder.encode(this).nospaces

  // version substitution possibly a bit flaky…
  def overrideVersion(ver: String): RawAppDescriptor =
    withDependencies {
      if (dependencies.isEmpty)
        dependencies
      else {
        val dep = {
          val dep0 = dependencies.head
          val idx = dep0.lastIndexOf(':')
          if (idx < 0)
            dep0 // ???
          else
            dep0.take(idx + 1) + ver
        }
        dep +: dependencies.tail
      }
    }

  def overrideVersion(verOpt: Option[String]): RawAppDescriptor =
    verOpt.fold(this)(overrideVersion(_))
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

  import argonaut.ArgonautShapeless._

  @data class RawGraalvmOptions(
    options: List[String] = Nil
  ) {
    def graalvmOptions: AppDescriptor.GraalvmOptions =
      AppDescriptor.GraalvmOptions(
        options
      )
  }

  object RawGraalvmOptions {

    import Codecs.{decodeObj, encodeObj}

    private final case class RawGraalvmOptionsJson(
      options: List[String] = Nil
    ) {
      def get: RawGraalvmOptions =
        RawGraalvmOptions()
          .withOptions(options)
    }

    private def optionsJson(opt: RawGraalvmOptions): RawGraalvmOptionsJson =
      RawGraalvmOptionsJson(opt.options)

    implicit val encoder: EncodeJson[RawGraalvmOptions] =
      EncodeJson.of[RawGraalvmOptionsJson].contramap(optionsJson)
    implicit val decoder: DecodeJson[RawGraalvmOptions] =
      DecodeJson.of[RawGraalvmOptionsJson].map(_.get)

  }

  private[install] implicit def validationNelToCats[L, R](v: coursier.util.ValidationNel[L, R]): ValidatedNel[L, R] =
    v.either match {
      case Left(h :: t) => Validated.invalid(NonEmptyList.of(h, t: _*))
      case Right(r) => Validated.validNel(r)
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
    prebuilt: Option[String] = None
  ) {
    def get: RawAppDescriptor = {
      var d = RawAppDescriptor(dependencies)
        .withRepositories(repositories)
        .withShared(shared)
        .withExclusions(exclusions)
        .withClassifiers(classifiers)
        .withArtifactTypes(artifactTypes)
        .withMainClass(mainClass)
        .withJavaOptions(javaOptions)
        .withScalaVersion(scalaVersion)
        .withName(name)
        .withGraalvm(graalvm)
        .withPrebuilt(prebuilt)
      for (t <- launcherType)
        d = d.withLauncherType(t)
      for (p <- properties)
        d = d.withProperties(p)
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
      prebuilt = desc.prebuilt
    )

  implicit val encoder: EncodeJson[RawAppDescriptor] =
    EncodeJson.of[RawAppDescriptorJson].contramap(descriptorJson)
  implicit val decoder: DecodeJson[RawAppDescriptor] =
    DecodeJson.of[RawAppDescriptorJson].map(_.get)

  def parse(input: String): Either[String, RawAppDescriptor] =
    Parse.decodeEither(input)(decoder)

}
