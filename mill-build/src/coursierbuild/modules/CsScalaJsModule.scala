package coursierbuild.modules

import coursierbuild.ScalaVersions
import mill._, mill.scalalib._, mill.scalajslib._

/** The Scala.js modules are all built with Scala 3 only, and aren't cross-built. */
trait CsScalaJsModule extends ScalaJSModule with CsScala3Module {
  def scalaJSVersion = ScalaVersions.scalaJs
}
