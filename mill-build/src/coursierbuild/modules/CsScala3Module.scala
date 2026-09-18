package coursierbuild.modules

import coursierbuild.ScalaVersions
import mill._, mill.scalalib._

/** A module built for Scala 3 only, as opposed to a cross-built one (see [[CsCrossJvmModule]]).
  *
  * Mixed in both by [[CsScalaJsModule]] - every Scala.js module is Scala 3 only - and by the Scala
  * 3 only JVM modules, so that the two agree on a single `scalaVersion` definition.
  */
trait CsScala3Module extends CsScalaModule {
  def scalaVersion = ScalaVersions.scala3
}
