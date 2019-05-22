package coursier.parse

import coursier.core.{Dependency, ModuleName}

sealed abstract class JavaOrScalaDependency extends Product with Serializable {
  def module: JavaOrScalaModule
  def dependency(scalaBinaryVersion: String, scalaVersion: String, platformName: String): Dependency

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
        JavaDependency(dep.copy(module = j.module))
      case s: JavaOrScalaModule.ScalaModule =>
        ScalaDependency(dep.copy(module = s.baseModule), s.fullCrossVersion, withPlatformSuffix = false)
    }

  final case class JavaDependency(dependency: Dependency) extends JavaOrScalaDependency {
    def module: JavaOrScalaModule.JavaModule =
      JavaOrScalaModule.JavaModule(dependency.module)
    def dependency(scalaBinaryVersion: String, scalaVersion: String, platformName: String): Dependency =
      dependency

    def withUnderlyingDependency(f: Dependency => Dependency): JavaDependency =
      copy(dependency = f(dependency))
  }
  final case class ScalaDependency(
    baseDependency: Dependency,
    fullCrossVersion: Boolean,
    withPlatformSuffix: Boolean
  ) extends JavaOrScalaDependency {
    def module: JavaOrScalaModule.ScalaModule =
      // FIXME withPlatformSuffix not supported in JavaOrScalaModule.ScalaModule
      JavaOrScalaModule.ScalaModule(baseDependency.module, fullCrossVersion)
    def dependency(scalaBinaryVersion: String, scalaVersion: String, platformName: String): Dependency = {

      val platformSuffix =
        if (withPlatformSuffix && platformName.nonEmpty) "_" + platformName
        else ""
      val scalaSuffix =
        if (fullCrossVersion) "_" + scalaVersion
        else "_" + scalaBinaryVersion

      val newName = baseDependency.module.name.value + platformSuffix + scalaSuffix

      baseDependency.copy(
        module = baseDependency.module.copy(
          name = ModuleName(newName)
        )
      )
    }

    def withUnderlyingDependency(f: Dependency => Dependency): ScalaDependency =
      copy(baseDependency = f(baseDependency))
  }
}
