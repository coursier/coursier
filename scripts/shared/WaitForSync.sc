
import $file.Util

import java.nio.file.Path

/**
 * Waits for a module published via Sonatype to be sync-ed to Maven Central
 *
 * @param coursierLauncher Path to a coursier launcher
 * @param module A module, like `"org:name:version"`
 * @param extraArgs Extra arguments to pass to coursier to fetch `module`, like `Seq("-r", "jitpack")`
 * @param testRepo A repository that has module already.
 * @param attempts Maximum number of attempts to check for the sync (one attempt per minute)
 */
def apply(
  coursierLauncher: String,
  module: String,
  extraArgs: Seq[String],
  testRepo: String,
  attempts: Int
): Unit = {

  val probeCommand = Seq(
    coursierLauncher,
    "resolve",
    module
  ) ++
  extraArgs

  val testCommand = probeCommand ++ Seq("-r", testRepo)

  Util.run(testCommand)

  val probeSuccess = Iterator.range(0, attempts)
    .map { i =>
      if (i > 0) {
        System.err.println(s"Not synced after $i attempts, waiting 1 minute")
        Thread.sleep(60000L)
      }
      Util.tryRun(probeCommand)
    }
    .exists(identity)

  if (!probeSuccess)
    sys.error(s"Probe command ${probeCommand.mkString(" ")} still failing after $attempts attempts")
}
