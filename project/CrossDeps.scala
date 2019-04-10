
import sbt._
import sbt.Keys._

import org.portablescala.sbtplatformdeps.PlatformDepsPlugin.autoImport._

object CrossDeps {

  import Def.setting

  // The setting / .value hoop-and-loop is necessary because of the expansion of the %%% macro, which references
  // other settings.

  def argonautShapeless = setting("com.github.alexarchambault" %%% "argonaut-shapeless_6.2" % "1.2.0-M10")
  def catsEffect = setting("org.typelevel" %%% "cats-effect" % "1.2.0")
  def fastParse = setting("com.lihaoyi" %%% "fastparse" % SharedVersions.fastParse)
  def scalazCore = setting("org.scalaz" %%% "scalaz-core" % SharedVersions.scalaz)
  def scalaJsDom = setting("org.scala-js" %%% "scalajs-dom" % "0.9.6")
  def utest = setting {
    val is213 = scalaVersion.value.startsWith("2.13")
    val ver =
      if (is213) "0.6.6"
      else "0.6.7"
    "com.lihaoyi" %%% "utest" % ver
  }
  def scalaJsJquery = setting("be.doeraene" %%% "scalajs-jquery" % "0.9.4")
  def scalaJsReact = setting("com.github.japgolly.scalajs-react" %%% "core" % "1.3.1")
}
