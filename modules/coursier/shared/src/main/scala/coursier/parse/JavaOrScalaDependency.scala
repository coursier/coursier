package coursier.parse

import dataclass.data

import coursier.core.{
  Classifier,
  Configuration,
  Dependency,
  DependencyManagement,
  Extension,
  MinimizedExclusions,
  Module,
  ModuleName,
  Organization,
  Type,
  VariantSelector
}
import coursier.version.VersionConstraint
import dependency.{CovariantSet, DependencyLike, ModuleLike}

import scala.collection.mutable

sealed abstract class JavaOrScalaDependency extends Product with Serializable {
  def module: JavaOrScalaModule
  def versionConstraint: VersionConstraint
  def exclude: Set[JavaOrScalaModule]
  def addExclude(excl: JavaOrScalaModule*): JavaOrScalaDependency
  def dependency(scalaBinaryVersion: String, scalaVersion: String, platformName: String): Dependency

  def withPlatform(platformSuffix: String): JavaOrScalaDependency

  def withUnderlyingDependency(f: Dependency => Dependency): JavaOrScalaDependency

  final def dependency(scalaVersion: String): Dependency = {
    val sbv = JavaOrScalaModule.scalaBinaryVersion(scalaVersion)
    dependency(sbv, scalaVersion, "")
  }

  @deprecated("Use versionConstraint instead", "2.1.25")
  def version: String = versionConstraint.asString
}

object JavaOrScalaDependency {

  def apply(mod: JavaOrScalaModule, dep: Dependency): JavaOrScalaDependency =
    mod match {
      case j: JavaOrScalaModule.JavaModule =>
        JavaDependency(dep.copy(module = j.module), Set.empty)
      case s: JavaOrScalaModule.ScalaModule =>
        ScalaDependency(
          dep.copy(module = s.baseModule),
          s.fullCrossVersion,
          withPlatformSuffix = false,
          Set.empty
        )
    }

  @data case class JavaDependency(dependency: Dependency, exclude: Set[JavaOrScalaModule])
      extends JavaOrScalaDependency {
    def module: JavaOrScalaModule.JavaModule =
      JavaOrScalaModule.JavaModule(dependency.module)
    def versionConstraint: VersionConstraint =
      dependency.versionConstraint
    def dependency(
      scalaBinaryVersion: String,
      scalaVersion: String,
      platformName: String
    ): Dependency =
      dependency.copy(minimizedExclusions = 
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
      copy(exclude = exclude ++ excl)
    def withUnderlyingDependency(f: Dependency => Dependency): JavaDependency =
      copy(dependency = f(dependency))
  }
  @data case class ScalaDependency(
    baseDependency: Dependency,
    fullCrossVersion: Boolean,
    withPlatformSuffix: Boolean,
    exclude: Set[JavaOrScalaModule]
  ) extends JavaOrScalaDependency {
    def module: JavaOrScalaModule.ScalaModule =
      // FIXME withPlatformSuffix not supported in JavaOrScalaModule.ScalaModule
      JavaOrScalaModule.ScalaModule(baseDependency.module, fullCrossVersion)
    def repr: String =
      s"$module:${if (withPlatformSuffix) ":" else ""}${baseDependency.versionConstraint.asString}"
    def versionConstraint: VersionConstraint =
      baseDependency.versionConstraint
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
        .copy(module = baseDependency.module.copy(name = ModuleName(newName)))
        .copy(minimizedExclusions = 
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
          dep.copy(module = 
            dep.module.copy(name = 
              ModuleName(dep.module.name.value + platformSuffix)
            )
          )
        }
      else
        this

    def addExclude(excl: JavaOrScalaModule*): ScalaDependency =
      copy(exclude = exclude ++ excl)
    def withUnderlyingDependency(f: Dependency => Dependency): ScalaDependency =
      copy(baseDependency = f(baseDependency))
  }

  private def inlineConfigKey = "$inlineConfiguration"
  private def classifierKey   = "classifier"
  private def extKey          = "ext"
  private def typeKey         = "type"
  private def bomKey          = "bom"
  private def overrideKey     = "override"
  private lazy val readKeys = Set(
    inlineConfigKey,
    classifierKey,
    extKey,
    typeKey,
    bomKey,
    overrideKey
  )
  def leftOverUserParams(dep: dependency.AnyDependency): Seq[(String, Option[String])] =
    dep.userParams.filter {
      case (key, _) =>
        !readKeys.contains(key)
    }
  @deprecated("Use from0 instead", "2.1.25")
  def from(dep: dependency.AnyDependency): Either[String, JavaOrScalaDependency] =
    from0(dep).map {
      case (dep0, _) =>
        dep0
    }
  def from0(dep: dependency.AnyDependency)
    : Either[String, (JavaOrScalaDependency, Map[String, Seq[Option[String]]])] = {

    var userParams = dep.userParamsMap

    var csDep = Dependency.create(
      Module(
        Organization(dep.module.organization),
        ModuleName(dep.module.name),
        dep.module.attributes
      ),
      VersionConstraint(dep.version)
    )
    val variantSelectorOpt =
      dep.userParamsMap.get(inlineConfigKey).flatMap(_.headOption).flatten match {
        case Some(config) =>
          userParams = userParams - inlineConfigKey
          Some(VariantSelector.ConfigurationBased(Configuration(config)))
        case None =>
          val variantParams = userParams
            .filter(_._1.startsWith("variant."))
            .collect {
              case (k, v) =>
                k -> v.flatten.filter(_.nonEmpty).lastOption
            }
            .collect {
              case (k, Some(v)) =>
                k -> v
            }
          if (variantParams.isEmpty) None
          else {
            userParams = userParams.filter {
              case (k, _) =>
                !variantParams.contains(k)
            }
            val variantParams0 = variantParams.map {
              case (k, v) =>
                val k0 = k.stripPrefix("variant.")
                VariantSelector.VariantMatcher.fromString(k0, v)
            }
            Some(VariantSelector.AttributesBased(variantParams0))
          }
      }
    for (variantSelector <- variantSelectorOpt)
      csDep = csDep.copy(variantSelector = variantSelector)

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
          userParams = userParams - classifierKey
          csDep = csDep.copy(publication = 
            csDep.publication.copy(classifier = Classifier(classifier))
          )
        case None =>
          errors += "Invalid empty classifier attribute"
      }
    for (extOpt <- userParams.get(extKey).flatMap(_.headOption))
      extOpt match {
        case Some(ext) =>
          userParams = userParams - extKey
          csDep = csDep.copy(publication = 
            csDep.publication.copy(ext = Extension(ext))
          )
        case None =>
          errors += "Invalid empty classifier attribute"
      }
    for (typeOpt <- userParams.get(typeKey).flatMap(_.headOption))
      typeOpt match {
        case Some(tpe) =>
          userParams = userParams - typeKey
          csDep = csDep.copy(publication = 
            csDep.publication.copy(`type` = Type(tpe))
          )
        case None =>
          errors += "Invalid empty classifier attribute"
      }
    val bomValues = userParams.get(bomKey).getOrElse(Nil)
    userParams = userParams - bomKey
    if (bomValues.exists(_.isEmpty))
      errors += "Invalid empty bom parameter"
    val bomOrErrors = bomValues.flatten.map { v =>
      dependency.parser.DependencyParser.parse(v.replace('%', ':')) match {
        case Left(error) => Left(error)
        case Right(bomDep) =>
          val expectedShape = DependencyLike(
            ModuleLike(
              bomDep.module.organization,
              bomDep.module.name,
              dependency.NoAttributes,
              Map.empty
            ),
            bomDep.version,
            CovariantSet.empty,
            Nil
          )
          if (bomDep == expectedShape)
            Right(
              (
                Module(
                  Organization(bomDep.module.organization),
                  ModuleName(bomDep.module.name),
                  Map.empty
                ),
                VersionConstraint(bomDep.version)
              )
            )
          else
            Left(s"Invalid BOM value '$v' (expected org%name%version)")
      }
    }
    val bomErrors = bomOrErrors.collect {
      case Left(e) => e
    }
    errors ++= bomErrors

    val boms = bomOrErrors.collect {
      case Right(modVer) => modVer
    }

    csDep = csDep.addBoms0(boms)

    val overrideValues = userParams.get(overrideKey).getOrElse(Nil)
    userParams = userParams - overrideKey
    if (overrideValues.exists(_.isEmpty))
      errors += "Invalid empty override parameter"
    val overrideOrErrors = overrideValues.flatten.map { v =>
      dependency.parser.DependencyParser.parse(v.replace('%', ':')) match {
        case Left(error) => Left(error)
        case Right(overrideDep) =>
          val expectedShape = DependencyLike(
            ModuleLike(
              overrideDep.module.organization,
              overrideDep.module.name,
              dependency.NoAttributes,
              Map.empty
            ),
            overrideDep.version,
            CovariantSet.empty,
            Nil
          )
          if (overrideDep == expectedShape)
            Right((
              DependencyManagement.Key(
                Organization(overrideDep.module.organization),
                ModuleName(overrideDep.module.name),
                Type.jar,
                Classifier.empty
              ),
              DependencyManagement.Values(
                Configuration.empty,
                VersionConstraint(overrideDep.version),
                MinimizedExclusions.zero,
                optional = false
              )
            ))
          else
            Left(s"Invalid override value '$v' (expected org%name%version)")
      }
    }
    val overrideErrors = overrideOrErrors.collect {
      case Left(e) => e
    }
    errors ++= overrideErrors

    val overrides = overrideOrErrors.collect {
      case Right(entry) => entry
    }

    csDep = csDep.addOverrides(overrides)

    if (errors.isEmpty) {
      val dep0 = dep.module.nameAttributes match {
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
      Right((dep0, userParams))
    }
    else
      Left(errors.mkString(", "))
  }
}
