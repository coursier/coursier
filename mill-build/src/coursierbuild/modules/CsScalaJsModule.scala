package coursierbuild.modules

import coursierbuild.Deps.ScalaVersions
import mill._, mill.scalalib._, mill.scalajslib._

trait CsScalaJsModule extends ScalaJSModule with CsScalaModule {
  def scalaJSVersion = ScalaVersions.scalaJs
  def scalacOptions = Task {
    val scala2Options =
      if (scalaVersion().startsWith("2.")) Seq("-P:scalajs:nowarnGlobalExecutionContext")
      else Nil
    super.scalacOptions() ++ scala2Options
  }
}
