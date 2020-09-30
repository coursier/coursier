package coursier.clitests

import java.io.File
import java.nio.charset.Charset
import java.util.concurrent.TimeUnit

import coursier.cache.internal.FileUtil

object LauncherTestUtil {

  lazy val isWindows = System.getProperty("os.name")
    .toLowerCase(java.util.Locale.ROOT)
    .contains("windows")

  lazy val launcher = {
    val path = sys.props.getOrElse(
      "coursier-test-launcher",
      sys.error("Java property coursier-test-launcher not set")
    )
    if (path.startsWith("./") || path.startsWith(".\\"))
      new File(path).getAbsolutePath
    else
      path
  }

  private def doRun[T](
    args: Seq[String],
    mapBuilder: ProcessBuilder => ProcessBuilder,
    f: Process => T
  ): T = {
    var p: Process = null
    try {
      val b = mapBuilder {
        new ProcessBuilder(args: _*)
          .inheritIO()
      }
      p = b.start()
      f(p)
    } finally {
      if (p != null) {
        val exited = p.waitFor(1L, TimeUnit.SECONDS)
        if (!exited)
          p.destroy()
      }
    }
  }

  private lazy val pathExt = Option(System.getenv("pathext"))
    .toSeq
    .flatMap(_.split(File.pathSeparator))
  def adaptCommandName(name: String, directory: File): String =
    if (isWindows && pathExt.nonEmpty && (name.startsWith("./") || name.startsWith(".\\")))
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
      case _ => args
    }

  def output(
    args: Seq[String],
    keepErrorOutput: Boolean,
    directory: File,
    extraEnv: Map[String, String]
  ): String =
    doRun(
      adaptArgs(args, directory),
      builder => {
        val env = builder.environment()
        for ((k, v) <- extraEnv)
          env.put(k, v)
        builder
          .redirectOutput(ProcessBuilder.Redirect.PIPE)
          .redirectErrorStream(keepErrorOutput)
          .directory(directory)
      },
      p => new String(FileUtil.readFully(p.getInputStream), Charset.defaultCharset())
    )

  def output(
    args: Seq[String],
    keepErrorOutput: Boolean,
    directory: File
  ): String =
    output(args, keepErrorOutput, directory, Map.empty[String, String])

  def output(
    args: Seq[String],
    keepErrorOutput: Boolean
  ): String =
    output(args, keepErrorOutput, directory = new File("."))

  def output(args: String*): String =
    output(args, keepErrorOutput = false)

  def tryRun(
    args: Seq[String],
    directory: File
  ): Int =
    doRun(
      adaptArgs(args, directory),
      builder => builder
        .directory(directory),
      _.waitFor()
    )

  def run(
    args: Seq[String],
    directory: File
  ): Unit = {
    val retCode = tryRun(args, directory)
    if (retCode != 0)
      sys.error(s"Error: command '${launcher}${args.map(" " + _).mkString}' exited with code $retCode")
  }
}
