package coursier.publish.sonatype.logger

trait SonatypeLogger {
  def listingProfiles(attempt: Int, total: Int): Unit = ()
  def listedProfiles(errorOpt: Option[Throwable]): Unit = ()
}

object SonatypeLogger {
  val nop: SonatypeLogger =
    new SonatypeLogger {}
}
