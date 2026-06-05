package coursier.jvm.util

import coursier.cache.internal.FileUtil

import java.nio.charset.Charset

import scala.concurrent.duration.Duration

trait CommandOutput {

  final def run(
    command: Seq[String],
    keepErrStream: Boolean
  ): Either[Int, String] =
    run(command, keepErrStream, Nil)

  def run(
    command: Seq[String],
    keepErrStream: Boolean,
    extraEnv: Seq[(String, String)]
  ): Either[Int, String]

  /** Like [[run]], but kills the spawned process (and returns a non-zero code) if it does not
    * complete within `timeout`. Implementations that cannot enforce a timeout fall back to the
    * regular, unbounded behaviour.
    */
  def run(
    command: Seq[String],
    keepErrStream: Boolean,
    extraEnv: Seq[(String, String)],
    timeout: Option[Duration]
  ): Either[Int, String] =
    run(command, keepErrStream, extraEnv)
}

object CommandOutput {

  private final class DefaultCommandOutput extends CommandOutput {
    def run(
      command: Seq[String],
      keepErrStream: Boolean,
      extraEnv: Seq[(String, String)]
    ): Either[Int, String] =
      run(command, keepErrStream, extraEnv, None)

    override def run(
      command: Seq[String],
      keepErrStream: Boolean,
      extraEnv: Seq[(String, String)],
      timeout: Option[Duration]
    ): Either[Int, String] = {
      val b = new ProcessBuilder(command: _*)
      b.redirectInput(ProcessBuilder.Redirect.INHERIT)
      b.redirectOutput(ProcessBuilder.Redirect.PIPE)
      b.redirectError(ProcessBuilder.Redirect.PIPE)
      b.redirectErrorStream(true)
      val env = b.environment()
      for ((k, v) <- extraEnv)
        env.put(k, v)
      val p = b.start()
      p.getOutputStream.close()

      // Watchdog: if the process outlives the timeout, kill it so that the readFully /
      // waitFor calls below unblock instead of hanging forever (see e.g. a jpackage-built
      // launcher spawning a child process that never returns).
      val finiteTimeout = timeout.filter(_.isFinite)
      val watchdogOpt = finiteTimeout.map { d =>
        val t = new Thread("coursier-command-output-timeout") {
          setDaemon(true)
          override def run(): Unit =
            try
              if (!p.waitFor(d.toMillis, java.util.concurrent.TimeUnit.MILLISECONDS)) {
                System.err.println(
                  s"Command ${command.mkString(" ")} timed out after $d, killing it"
                )
                p.destroyForcibly()
              }
            catch {
              case _: InterruptedException => // process completed in time, nothing to do
            }
        }
        t.start()
        t
      }

      try {
        val output  = new String(FileUtil.readFully(p.getInputStream), Charset.defaultCharset())
        val retCode = p.waitFor()
        if (retCode == 0)
          Right(output)
        else
          Left(retCode)
      }
      finally
        watchdogOpt.foreach(_.interrupt())
    }
  }

  def default(): CommandOutput =
    new DefaultCommandOutput

  def apply(
    f: (Seq[String], Boolean, Seq[(String, String)]) => Either[Int, String]
  ): CommandOutput =
    new CommandOutput {
      def run(
        command: Seq[String],
        keepErrStream: Boolean,
        extraEnv: Seq[(String, String)]
      ): Either[Int, String] =
        f(command, keepErrStream, extraEnv)
    }

}
