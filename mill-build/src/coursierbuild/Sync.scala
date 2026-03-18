package coursierbuild

import java.nio.file.Path

object Sync {

  /** Waits for a module published via Sonatype to be sync-ed to Maven Central
    *
    * @param coursierLauncher
    *   Path to a coursier launcher
    * @param module
    *   A module, like `"org:name:version"`
    * @param extraArgs
    *   Extra arguments to pass to coursier to fetch `module`, like `Seq("-r", "jitpack")`
    * @param attemptsOpt
    *   Maximum number of attempts to check for the sync (one attempt per minute)
    */
  def waitForSync(
    coursierLauncher: String,
    module: String,
    extraArgs: Seq[String],
    attemptsOpt: Option[Int]
  ): Unit = {

    val probeCommand = Seq(
      coursierLauncher,
      "resolve",
      "--ttl",
      "0s",
      module
    ) ++
      extraArgs

    val it = attemptsOpt match {
      case None           => Iterator.from(0)
      case Some(attempts) => Iterator.range(0, attempts)
    }
    val probeSuccess = it
      .map { i =>
        if (i > 0) {
          System.err.println(s"Not synced after $i attempts, waiting 1 minute")
          Thread.sleep(60000L)
        }
        val res = os.proc(probeCommand).call(
          stdin = os.Inherit,
          stdout = os.Inherit,
          stderr = os.Inherit,
          check = false
        )
        res.exitCode == 0
      }
      .exists(identity)

    if (!probeSuccess)
      sys.error(
        s"Probe command ${probeCommand.mkString(" ")} still failing after ${attemptsOpt.map(_.toString).getOrElse("?")} attempts"
      )
  }
}
