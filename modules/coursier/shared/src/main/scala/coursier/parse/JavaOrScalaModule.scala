package coursier.parse

import coursier.core.{Module, ModuleName}

sealed abstract class JavaOrScalaModule extends Product with Serializable {
  def module(scalaBinaryVersion: String, scalaVersion: String): Module

  final def module(scalaVersion: String): Module = {
    val sbv = JavaOrScalaModule.scalaBinaryVersion(scalaVersion)
    module(sbv, scalaVersion)
  }
}

object JavaOrScalaModule {

  def scalaBinaryVersion(scalaVersion: String): String =
    if (scalaVersion.contains("-M") || scalaVersion.contains("-RC"))
      scalaVersion
    else
      scalaVersion.split('.').take(2).mkString(".")

  final case class JavaModule(module: Module) extends JavaOrScalaModule {
    override def toString =
      module.toString
    def module(scalaBinaryVersion: String, scalaVersion: String): Module =
      module
  }
  final case class ScalaModule(
    baseModule: Module,
    fullCrossVersion: Boolean
  ) extends JavaOrScalaModule {
    override def toString = {
      val sep = if (fullCrossVersion) ":::" else "::"
      s"${baseModule.organization.value}$sep${baseModule.nameWithAttributes}"
    }
    def module(scalaBinaryVersion: String, scalaVersion: String): Module = {

      val scalaSuffix =
        if (fullCrossVersion) "_" + scalaVersion
        else "_" + scalaBinaryVersion

      val newName = baseModule.name.value + scalaSuffix

      baseModule.copy(
        name = ModuleName(newName)
      )
    }
  }
}
