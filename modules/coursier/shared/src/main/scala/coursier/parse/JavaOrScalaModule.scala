package coursier.parse

import coursier.core.{Module, ModuleName}
import dataclass.data

sealed abstract class JavaOrScalaModule extends Product with Serializable {
  def attributes: Map[String, String]
  def module(scalaBinaryVersion: String, scalaVersion: String): Module

  final def module(scalaVersion: String): Module = {
    val sbv = JavaOrScalaModule.scalaBinaryVersion(scalaVersion)
    module(sbv, scalaVersion)
  }
}

object JavaOrScalaModule {

  // Copied from https://github.com/sbt/librarymanagement/blob/5ef0af2486d19cc237684000ef34c99191b88dfd/core/src/main/scala/sbt/internal/librarymanagement/cross/CrossVersionUtil.scala#L23-L32
  private val longPattern  = """\d{1,19}"""
  private val basicVersion = raw"""($longPattern)\.($longPattern)\.($longPattern)"""
  private val tagPattern   = raw"""(?:\w+(?:\.\w+)*)"""
  private val ReleaseV     = raw"""$basicVersion""".r
  private val BinCompatV   = raw"""$basicVersion(-$tagPattern)?-bin(-.*)?""".r
  private val NonReleaseV_n =
    raw"""$basicVersion((?:-$tagPattern)*)""".r // 0-n word suffixes, with leading dashes
  private val NonReleaseV_1 = raw"""$basicVersion(-$tagPattern)""".r // 1 word suffix, after a dash

  def scalaBinaryVersion(scalaVersion: String): String =
    // Directly inspired from https://github.com/sbt/librarymanagement/blob/5ef0af2486d19cc237684000ef34c99191b88dfd/core/src/main/scala/sbt/internal/librarymanagement/cross/CrossVersionUtil.scala#L87
    if (scalaVersion.startsWith("3."))
      scalaVersion match {
        case ReleaseV(maj, _, _) =>
          maj
        case NonReleaseV_n(maj, min, patch, _) if min.toLong > 0 || patch.toLong > 0 =>
          maj
        case BinCompatV(maj, min, patch, stageOrNull, _) =>
          val stage = if (stageOrNull != null) stageOrNull else ""
          scalaBinaryVersion(s"$maj.$min.$patch$stage")
        case _ =>
          scalaVersion
      }
    else
      scalaVersion match {
        case ReleaseV(maj, min, _) =>
          s"$maj.$min"
        case BinCompatV(maj, min, _, _, _) =>
          s"$maj.$min"
        case NonReleaseV_1(maj, min, patch, _) if patch.toLong > 0 =>
          s"$maj.$min"
        case _ =>
          scalaVersion
      }

  @data class JavaModule(module: Module) extends JavaOrScalaModule {
    def attributes: Map[String, String] = module.attributes
    override def toString =
      module.toString
    def module(scalaBinaryVersion: String, scalaVersion: String): Module =
      module
  }
  @data class ScalaModule(
    baseModule: Module,
    fullCrossVersion: Boolean
  ) extends JavaOrScalaModule {
    def attributes: Map[String, String] = baseModule.attributes
    override def toString = {
      val sep = if (fullCrossVersion) ":::" else "::"
      s"${baseModule.organization.value}$sep${baseModule.nameWithAttributes}"
    }
    def module(scalaBinaryVersion: String, scalaVersion: String): Module = {

      val scalaSuffix =
        if (fullCrossVersion) "_" + scalaVersion
        else "_" + scalaBinaryVersion

      val newName = baseModule.name.value + scalaSuffix

      baseModule.withName(ModuleName(newName))
    }
  }
}
