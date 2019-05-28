package coursier.publish.sonatype.logger

import java.io.PrintStream

final class BatchSonatypeLogger(out: PrintStream, verbosity: Int) extends SonatypeLogger {
  override def listingProfiles(attempt: Int, total: Int): Unit =
    if (verbosity >= 0) {
      val extra =
        if (attempt == 0) ""
        else s" (attempt $attempt / $total)"
      out.println("Listing Sonatype profiles..." + extra)
    }
  override def listedProfiles(errorOpt: Option[Throwable]): Unit = {

    val msgOpt =
      if (errorOpt.isEmpty) {
        if (verbosity >= 1)
          Some("Listed Sonatype profiles")
        else
          None
      } else
        Some("Fail to list Sonatype profiles")

    for (msg <- msgOpt)
      out.println(s"$msg")
  }
}
