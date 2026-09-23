package coursierbuild

object Versions {
  def cats          = "2.13.0"
  def http4s        = "0.23.37"
  def jniUtils      = "0.4.0"
  def jsoniterScala = "2.41.0"
  def junit         = "4.13.2"
  def scalaz        = "7.2.36"

  // The Scala 2 modules still support Java 8, and jsoniter-scala 2.13.9 and later target Java 11,
  // so they stay at the last release with Java 8 class files. The Scala 3 modules only support
  // Java 17, and follow the latest releases via jsoniterScala above. The pin has to live here
  // rather than in .scala-steward.conf: Scala Steward matches pins on the artifact name without
  // its Scala suffix, so a pin there would block the Scala 3 line too.
  def jsoniterScalaScala2 = "2.13.8" // scala-steward:off

  def sbtCoursier  = "2.1.4"
  def graalVmJvmId = "liberica-nik:25.0.2"
  def scalaCli     = "1.14.0"
  def csDocker     = "2.1.25"
  def csQemu       = "9.2.1-1"
}
