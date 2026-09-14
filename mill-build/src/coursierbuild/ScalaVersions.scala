package coursierbuild

object ScalaVersions {
  def scala3   = "3.9.0"
  def scala213 = "2.13.18"
  def scala212 = "2.12.21"
  // TODO SCALA_213_BASELINE search for this TODO in the codebase
  // for cleanup tasks when we move to Scala 2.13 as as the baseline
  val all = Seq(scala213, scala212)

  def scalaJs = "1.22.0"
}
