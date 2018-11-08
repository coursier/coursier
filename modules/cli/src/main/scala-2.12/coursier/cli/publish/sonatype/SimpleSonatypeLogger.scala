package coursier.cli.publish.sonatype

import java.io.{OutputStream, OutputStreamWriter}

import coursier.Terminal.Ansi

final class SimpleSonatypeLogger(out: OutputStreamWriter, verbosity: Int) extends SonatypeLogger {
  override def listingProfiles(attempt: Int, total: Int): Unit =
    if (verbosity >= 0) {
      val extra =
        if (attempt == 0) ""
        else s" (attempt $attempt / $total)"
      out.write("Listing Sonatype profiles..." + extra)
      out.flush()
    }
  override def listedProfiles(errorOpt: Option[Throwable]): Unit = {
    if (verbosity >= 0) {
      out.clearLine(2)
      out.write('\n')
      out.up(1)
      out.flush()
    }

    val msgOpt =
      if (errorOpt.isEmpty) {
        if (verbosity >= 1)
          Some("Listed Sonatype profiles")
        else
          None
      } else
        Some("Fail to list Sonatype profiles")

    for (msg <- msgOpt) {
      out.write(s"$msg\n")
      out.flush()
    }
  }
}

object SimpleSonatypeLogger {
  def create(out: OutputStream, verbosity: Int): SonatypeLogger =
    new SimpleSonatypeLogger(new OutputStreamWriter(out), verbosity)
}
