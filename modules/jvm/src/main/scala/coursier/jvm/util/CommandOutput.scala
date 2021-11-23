package coursier.jvm.util

import coursier.cache.internal.FileUtil

import java.nio.charset.Charset

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
}

object CommandOutput {

  private final class DefaultCommandOutput extends CommandOutput {
    def run(
      command: Seq[String],
      keepErrStream: Boolean,
      extraEnv: Seq[(String, String)]
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
      val output  = new String(FileUtil.readFully(p.getInputStream), Charset.defaultCharset())
      val retCode = p.waitFor()
      if (retCode == 0)
        Right(output)
      else
        Left(retCode)
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
