package coursierbuild

object ScalaVersions {
  def scala3   = "3.9.0"
  def scala213 = "2.13.18"
  def scala212 = "2.12.21"
  // TODO SCALA_213_BASELINE search for this TODO in the codebase
  // for cleanup tasks when we move to Scala 2.13 as as the baseline
  //
  // Scala 2.12 is only supported by the modules sbt-coursier depends on (util, core, cache,
  // coursier, sbt-maven-repository - see scripts/publish-local-coursier.sh in
  // coursier/sbt-coursier), and by the modules testing them. Everything else is built for
  // Scala 2.13 and 3 only.
  val all            = Seq(scala213, scala212, scala3)
  val allButScala212 = Seq(scala213, scala3)
  // Scala.js modules are only built for Scala 2.13 and 3
  val allJsScala2 = Seq(scala213)
  val allJs       = allJsScala2 :+ scala3

  def scalaJs = "1.22.0"
}
