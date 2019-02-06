package coursier.bootstrap

import java.io.InputStream
import java.nio.charset.Charset
import java.nio.file.{Files, Path}
import java.util.Locale

import scala.io.{Codec, Source}

object LauncherBat {

  def isWindows: Boolean =
    sys
      .props
      .get("os.name")
      .map(_.toLowerCase(Locale.ROOT))
      .exists(_.contains("windows"))

  lazy val template: String = {

    var is: InputStream = null

    try {
      is = getClass
        .getClassLoader
        .getResourceAsStream("coursier/bootstrap/launcher.bat")
      Source.fromInputStream(is)(Codec.UTF8).mkString
    } finally {
      if (is != null)
        is.close()
    }
  }

  def apply(jvmOpts: String): String =
    template
      .replace("@JVM_OPTS@", jvmOpts)

  def create(
    output: Path,
    javaOpts: Seq[String] = Nil,
    charset: Charset = Charset.defaultCharset()
  ): Unit = {

    // no escaping for javaOpts :|
    val content = LauncherBat(javaOpts.mkString(" "))
    Files.write(output, content.getBytes(charset))
  }

}
