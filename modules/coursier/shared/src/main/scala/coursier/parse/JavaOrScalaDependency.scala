package coursier.parse

import coursier.core.{Dependency, ModuleName}

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
        ScalaDependency(dep.withModule(s.baseModule), s.fullCrossVersion, withPlatformSuffix = false, Set.empty)
    }

  final case class JavaDependency(dependency: Dependency, exclude: Set[JavaOrScalaModule]) extends JavaOrScalaDependency {
    def module: JavaOrScalaModule.JavaModule =
      JavaOrScalaModule.JavaModule(dependency.module)
    def version: String =
      dependency.version
    def dependency(scalaBinaryVersion: String, scalaVersion: String, platformName: String): Dependency =
      dependency

    def withPlatform(platformSuffix: String): JavaDependency =
      this

    def addExclude(excl: JavaOrScalaModule*): JavaDependency =
      copy(exclude = exclude ++ excl)
    def withUnderlyingDependency(f: Dependency => Dependency): JavaDependency =
      copy(dependency = f(dependency))
  }
  final case class ScalaDependency(
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
    def dependency(scalaBinaryVersion: String, scalaVersion: String, platformName: String): Dependency = {

      val platformSuffix =
        if (withPlatformSuffix && platformName.nonEmpty) "_" + platformName
        else ""
      val scalaSuffix =
        if (fullCrossVersion) "_" + scalaVersion
        else "_" + scalaBinaryVersion

      val newName = baseDependency.module.name.value + platformSuffix + scalaSuffix

      baseDependency.withModule(baseDependency.module.withName(ModuleName(newName)))
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
      copy(exclude = exclude ++ excl)
    def withUnderlyingDependency(f: Dependency => Dependency): ScalaDependency =
      copy(baseDependency = f(baseDependency))
  }
}
