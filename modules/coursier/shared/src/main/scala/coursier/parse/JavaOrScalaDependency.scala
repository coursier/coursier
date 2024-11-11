package coursier.parse

import coursier.core.{
  Classifier,
  Configuration,
  Dependency,
  Extension,
  MinimizedExclusions,
  Module,
  ModuleName,
  Organization,
  Type
}
import dataclass.data

import scala.collection.mutable

sealed abstract class JavaOrScalaDependency extends Product with Serializable {
  def module: JavaOrScalaModule
  def version: String
  def exclude: Set[JavaOrScalaModule]
  def addExclude(excl: JavaOrScalaModule*): JavaOrScalaDependency
  def dependency(scalaBinaryVersion: String, scalaVersion: String, platformName: String): Dependency

  def withPlatform(platformSuffix: String): JavaOrScalaDependency

  def withUnderlyingDependency(f: Dependency => Dependency): JavaOrScalaDependency

  final def dependency(scalaVersion: String): Dependency = {
    val sbv = JavaOrScalaModule.scalaBinaryVersion(scalaVersion)
    dependency(sbv, scalaVersion, "")
  }
}

object JavaOrScalaDependency {

  def apply(mod: JavaOrScalaModule, dep: Dependency): JavaOrScalaDependency =
    mod match {
      case j: JavaOrScalaModule.JavaModule =>
        JavaDependency(dep.withModule(j.module), Set.empty)
      case s: JavaOrScalaModule.ScalaModule =>
        ScalaDependency(
          dep.withModule(s.baseModule),
          s.fullCrossVersion,
          withPlatformSuffix = false,
          Set.empty
        )
    }

  @data class JavaDependency(dependency: Dependency, exclude: Set[JavaOrScalaModule])
      extends JavaOrScalaDependency {
    def module: JavaOrScalaModule.JavaModule =
      JavaOrScalaModule.JavaModule(dependency.module)
    def version: String =
      dependency.version
    def dependency(
      scalaBinaryVersion: String,
      scalaVersion: String,
      platformName: String
    ): Dependency =
      dependency.withMinimizedExclusions(
        dependency.minimizedExclusions.join(
          MinimizedExclusions(
            exclude.map(_.module(scalaBinaryVersion, scalaVersion)).map { mod =>
              (mod.organization, mod.name)
            }
          )
        )
      )

    def withPlatform(platformSuffix: String): JavaDependency =
      this

    def addExclude(excl: JavaOrScalaModule*): JavaDependency =
      withExclude(exclude ++ excl)
    def withUnderlyingDependency(f: Dependency => Dependency): JavaDependency =
      withDependency(f(dependency))
  }
  @data class ScalaDependency(
    baseDependency: Dependency,
    fullCrossVersion: Boolean,
    withPlatformSuffix: Boolean,
    exclude: Set[JavaOrScalaModule]
  ) extends JavaOrScalaDependency {
    def module: JavaOrScalaModule.ScalaModule =
      // FIXME withPlatformSuffix not supported in JavaOrScalaModule.ScalaModule
      JavaOrScalaModule.ScalaModule(baseDependency.module, fullCrossVersion)
    def repr: String =
      s"$module:${if (withPlatformSuffix) ":" else ""}${baseDependency.version}"
    def version: String =
      baseDependency.version
    def dependency(
      scalaBinaryVersion: String,
      scalaVersion: String,
      platformName: String
    ): Dependency = {

      val platformSuffix =
        if (withPlatformSuffix && platformName.nonEmpty) "_" + platformName
        else ""
      val scalaSuffix =
        if (fullCrossVersion) "_" + scalaVersion
        else "_" + scalaBinaryVersion

      val newName = baseDependency.module.name.value + platformSuffix + scalaSuffix

      baseDependency
        .withModule(baseDependency.module.withName(ModuleName(newName)))
        .withMinimizedExclusions(
          baseDependency.minimizedExclusions.join(
            MinimizedExclusions(
              exclude.map(_.module(scalaBinaryVersion, scalaVersion)).map { mod =>
                (mod.organization, mod.name)
              }
            )
          )
        )
    }

    def withPlatform(platformSuffix: String): ScalaDependency =
      if (withPlatformSuffix)
        withUnderlyingDependency { dep =>
          dep.withModule(
            dep.module.withName(
              ModuleName(dep.module.name.value + platformSuffix)
            )
          )
        }
      else
        this

    def addExclude(excl: JavaOrScalaModule*): ScalaDependency =
      withExclude(exclude ++ excl)
    def withUnderlyingDependency(f: Dependency => Dependency): ScalaDependency =
      withBaseDependency(f(baseDependency))
  }

  private def inlineConfigKey = "$inlineConfiguration"
  private def classifierKey   = "classifier"
  private def extKey          = "ext"
  private def typeKey         = "type"
  private lazy val readKeys = Set(
    inlineConfigKey,
    classifierKey,
    extKey,
    typeKey
  )
  def leftOverUserParams(dep: dependency.AnyDependency): Seq[(String, Option[String])] =
    dep.userParams.filter {
      case (key, _) =>
        !readKeys.contains(key)
    }
  def from(dep: dependency.AnyDependency): Either[String, JavaOrScalaDependency] = {
    var csDep = Dependency(
      Module(
        Organization(dep.module.organization),
        ModuleName(dep.module.name),
        dep.module.attributes
      ),
      dep.version
    )
    val (userParams, configOpt) =
      dep.userParamsMap.get(inlineConfigKey).flatMap(_.headOption) match {
        case Some(configOpt0) =>
          (dep.userParamsMap - inlineConfigKey, configOpt0: Option[String])
        case None =>
          (dep.userParamsMap, None)
      }
    for (config <- configOpt)
      csDep = csDep.withConfiguration(Configuration(config))

    val excludes = dep.exclude.map { mod =>
      mod.nameAttributes match {
        case dependency.NoAttributes =>
          JavaOrScalaModule.JavaModule(
            Module(
              Organization(mod.organization),
              ModuleName(mod.name),
              mod.attributes
            )
          )
        case scalaAttr: dependency.ScalaNameAttributes =>
          if (scalaAttr.platform.nonEmpty)
            ???
          else
            JavaOrScalaModule.ScalaModule(
              Module(Organization(mod.organization), ModuleName(mod.name), mod.attributes),
              fullCrossVersion = scalaAttr.fullCrossVersion.getOrElse(false)
            )
      }
    }

    val errors = new mutable.ListBuffer[String]

    for (classifierOpt <- userParams.get(classifierKey).flatMap(_.headOption))
      classifierOpt match {
        case Some(classifier) =>
          csDep = csDep.withPublication(
            csDep.publication.withClassifier(Classifier(classifier))
          )
        case None =>
          errors += "Invalid empty classifier attribute"
      }
    for (extOpt <- userParams.get(extKey).flatMap(_.headOption))
      extOpt match {
        case Some(ext) =>
          csDep = csDep.withPublication(
            csDep.publication.withExt(Extension(ext))
          )
        case None =>
          errors += "Invalid empty classifier attribute"
      }
    for (typeOpt <- userParams.get(typeKey).flatMap(_.headOption))
      typeOpt match {
        case Some(tpe) =>
          csDep = csDep.withPublication(
            csDep.publication.withType(Type(tpe))
          )
        case None =>
          errors += "Invalid empty classifier attribute"
      }

    if (errors.isEmpty)
      Right {
        dep.module.nameAttributes match {
          case dependency.NoAttributes =>
            JavaOrScalaDependency.JavaDependency(csDep, excludes.toSet)
          case scalaAttr: dependency.ScalaNameAttributes =>
            JavaOrScalaDependency.ScalaDependency(
              csDep,
              fullCrossVersion = scalaAttr.fullCrossVersion.getOrElse(false),
              withPlatformSuffix = scalaAttr.platform.getOrElse(false),
              exclude = excludes.toSet
            )
        }
      }
    else
      Left(errors.mkString(", "))
  }
}
