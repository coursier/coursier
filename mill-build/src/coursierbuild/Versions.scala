package coursierbuild

object Versions {
  def cats     = "2.13.0"
  def http4s   = "0.23.37"
  def jniUtils = "0.4.0"
  // jsoniter-scala 2.13.9 and later target Java 11, and the Scala 2 artifacts still support
  // Java 8, so the Scala 2 modules stay at the last release with Java 8 class files. The pin
  // has to live here rather than in .scala-steward.conf: Scala Steward matches pins on the
  // artifact name without its Scala suffix, so a pin there would block the Scala 3 line too.
  def jsoniterScala = "2.13.8" // scala-steward:off
  // The Scala 3 modules only support Java 17, so they follow the latest jsoniter-scala releases
  def jsoniterScalaScala3 = "2.41.0"
  def junit               = "4.13.2"
  def scalaz              = "7.2.36"

  def sbtCoursier  = "2.1.4"
  def graalVmJvmId = "liberica-nik:25.0.2"
  def scalaCli     = "1.14.0"
  def csDocker     = "2.1.25"
  def csQemu       = "9.2.1-1"
}
