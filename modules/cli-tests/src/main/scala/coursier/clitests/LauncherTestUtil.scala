package coursier.clitests

import java.io.File

import scala.io.Codec
import scala.util.Properties

object LauncherTestUtil {

  lazy val launcher = {
    val path = sys.props.getOrElse(
      "coursier-test-launcher",
      sys.error("Java property coursier-test-launcher not set")
    )
    if (path.startsWith("./") || path.startsWith(".\\"))
      os.Path(path, os.pwd).toString
    else
      path
  }

  private lazy val pathExt = Option(System.getenv("pathext"))
    .toSeq
    .flatMap(_.split(File.pathSeparator))
  def adaptCommandName(name: String, directory: File): String =
    if (Properties.isWin && pathExt.nonEmpty && (name.startsWith("./") || name.startsWith(".\\")))
      pathExt
        .iterator
        .map(ext => new File(directory, name + ext))
        .filter(_.canExecute())
        .map(_.getCanonicalPath)
        .toStream
        .headOption
        .getOrElse(name)
    else
      name
  private def adaptArgs(args: Seq[String], directory: File): Seq[String] =
    args match {
      case Seq(h, t @ _*) => adaptCommandName(h, directory) +: t
      case _              => args
    }

  def output(
    args: Seq[String],
    keepErrorOutput: Boolean,
    directory: File,
    extraEnv: Map[String, String]
  ): String =
    os.proc(adaptArgs(args, directory))
      .call(
        cwd = os.Path(directory, os.pwd),
        stderr = if (keepErrorOutput) os.Pipe else os.Inherit,
        mergeErrIntoOut = keepErrorOutput,
        env = extraEnv
      )
      .out.text(Codec.default)

  def output(
    args: Seq[String],
    keepErrorOutput: Boolean,
    directory: File
  ): String =
    os.proc(adaptArgs(args, directory))
      .call(
        cwd = os.Path(directory, os.pwd),
        stderr = if (keepErrorOutput) os.Pipe else os.Inherit,
        mergeErrIntoOut = keepErrorOutput
      )
      .out.text(Codec.default)

  def run(
    args: Seq[String],
    directory: File
  ): Unit =
    os.proc(adaptArgs(args, directory))
      .call(cwd = os.Path(directory, os.pwd))
}
