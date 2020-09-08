package coursier.cli

import java.nio.charset.Charset
import java.util.concurrent.TimeUnit

import coursier.cache.internal.FileUtil

object LauncherTestUtil {

  lazy val launcher = sys.props.getOrElse(
    "coursier-test-launcher",
    sys.error("Java property coursier-test-launcher not set")
  )

  def output(args: Seq[String], keepErrorOutput: Boolean): String = {
    var p: Process = null
    try {
      p = new ProcessBuilder(Seq(launcher) ++ args: _*)
        .inheritIO()
        .redirectOutput(ProcessBuilder.Redirect.PIPE)
        .redirectErrorStream(keepErrorOutput)
        .start()
      new String(FileUtil.readFully(p.getInputStream), Charset.defaultCharset())
    } finally {
      if (p != null) {
        val exited = p.waitFor(1L, TimeUnit.SECONDS)
        if (!exited)
          p.destroy()
      }
    }
  }

  def output(args: String*): String =
    output(args, keepErrorOutput = false)

}
