package coursierbuild.modules

import coursierbuild.Deps.ScalaVersions
import mill._, mill.scalalib._, mill.scalajslib._

trait CsScalaJsModule extends ScalaJSModule with CsScalaModule {
  def scalaJSVersion = ScalaVersions.scalaJs
  def scalacOptions  = super.scalacOptions() ++ Seq(
    "-P:scalajs:nowarnGlobalExecutionContext"
  )
}
