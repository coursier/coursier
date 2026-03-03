package coursierbuild.modules

import coursierbuild.Deps.ScalaVersions
import mill.*
import mill.scalajslib.*
import mill.scalalib.*

trait CsScalaJsModule extends ScalaJSModule with CsScalaModule {
  def scalaJSVersion = ScalaVersions.scalaJs
  def scalacOptions = super.scalacOptions() ++ Seq(
    "-P:scalajs:nowarnGlobalExecutionContext"
  )
}
