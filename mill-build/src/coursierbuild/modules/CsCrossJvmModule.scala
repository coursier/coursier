package coursierbuild.modules

import mill._, mill.scalalib._

/** The JVM flavour of the modules that are cross-built for Scala 2.12 / 2.13 / 3.
  *
  * Their Scala.js counterparts aren't cross-built - they are Scala 3 only, see [[CsScalaJsModule]].
  */
trait CsCrossJvmModule extends CrossSbtModule with CsModule
