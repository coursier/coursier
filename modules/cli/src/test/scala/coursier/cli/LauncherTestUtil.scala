package coursier.cli

import java.io.File
import java.nio.charset.Charset
import java.util.concurrent.TimeUnit

import coursier.cache.internal.FileUtil

object LauncherTestUtil {

  lazy val launcher = sys.props.getOrElse(
    "coursier-test-launcher",
    sys.error("Java property coursier-test-launcher not set")
  )

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

  def output(
    args: Seq[String],
    keepErrorOutput: Boolean,
    addCsLauncher: Boolean,
    directory: File
  ): String =
    doRun(
      if (addCsLauncher) launcher +: args else args,
      builder => builder
        .redirectOutput(ProcessBuilder.Redirect.PIPE)
        .redirectErrorStream(keepErrorOutput)
        .directory(directory),
      p => new String(FileUtil.readFully(p.getInputStream), Charset.defaultCharset())
    )

  def output(
    args: Seq[String],
    keepErrorOutput: Boolean
  ): String =
    output(args, keepErrorOutput, addCsLauncher = true, directory = new File("."))

  def output(args: String*): String =
    output(args, keepErrorOutput = false)

  def run(
    args: Seq[String],
    addCsLauncher: Boolean,
    directory: File
  ): Unit =
    doRun(
      if (addCsLauncher) launcher +: args else args,
      builder => builder
        .directory(directory),
      p => {
        val retCode = p.waitFor()
        if (retCode != 0)
          sys.error(s"Error: command '${launcher}${args.map(" " + _).mkString}' exited with code $retCode")
      }
    )

  def run(args: String*): Unit =
    run(
      args = args,
      addCsLauncher = true,
      directory = new File(".")
    )

}
