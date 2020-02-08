package coursier.launcher

import java.io.InputStream
import java.nio.charset.{Charset, StandardCharsets}

import dataclass.data

import scala.io.{Codec, Source}

@data class Preamble(
  kind: Preamble.Kind = Preamble.Kind.Sh,
  javaOpts: Seq[String] = Nil,
  jarPath: Option[String] = None,
  command: Option[String] = None
) {

  def withOsKind(isWindows: Boolean): Preamble =
    withKind(if (isWindows) Preamble.Kind.Bat else Preamble.Kind.Sh)

  def callsItself(isWindows: Boolean): Preamble = {
    val updatedJarPath =
      if (isWindows) "%~dpnx0"
      else "$0"
    withJarPath(updatedJarPath)
  }

  def withJarPath(path: String): Preamble =
    withJarPath(Some(path))
  def withCommand(command: String): Preamble =
    withCommand(Some(command))

  def sh: Array[Byte] = {
    val lines = command match {
      case None =>
        val javaCmd = Seq("java") ++
          // escaping possibly a bit loose :-|
          javaOpts.map(s => "'" + s.replace("'", "\\'") + "'") ++
          Seq("$JAVA_OPTS", "\"$@\"")

        Seq(
          "#!/usr/bin/env sh",
          Preamble.shArgsPartitioner(jarPath.getOrElse("$0")),
          "exec " + javaCmd.mkString(" ")
        )

      case Some(c) =>
        Seq(
          "#!/usr/bin/env sh",
          "exec " + c + " \"$@\""
        )
    }

    lines.mkString("", "\n", "\n").getBytes(StandardCharsets.UTF_8)
  }

  def bat: Array[Byte] = {
    val content = command match {
      case None =>
        // FIXME no escaping for javaOpts :|
        Preamble.batJarTemplate
          .replace("@JVM_OPTS@", javaOpts.mkString(" "))
          .replace("@JAR_PATH@", jarPath.getOrElse("%~dpnx0"))
      case Some(c) =>
        // FIXME no escaping :|
        Preamble.batCommandTemplate
          .replace("@COMMAND@", c)
    }
    content.getBytes(Charset.defaultCharset())
  }

  def value: Array[Byte] =
    kind match {
      case Preamble.Kind.Sh => sh
      case Preamble.Kind.Bat => bat
    }
}

object Preamble {

  sealed abstract class Kind extends Product with Serializable
  object Kind {
    case object Sh extends Kind
    case object Bat extends Kind
  }

  private def readResource(path: String): String = {

    var is: InputStream = null

    try {
      is = getClass
        .getClassLoader
        .getResourceAsStream(path)
      Source.fromInputStream(is)(Codec.UTF8).mkString
    } finally {
      if (is != null)
        is.close()
    }
  }

  private lazy val batJarTemplate: String =
    readResource("coursier/launcher/jar-launcher.bat")
  private lazy val batCommandTemplate: String =
    readResource("coursier/launcher/launcher.bat")


  private def shArgsPartitioner(jarPath: String) = {
    val bs = "\\"
    s"""nargs=$$#
       |
       |i=1; while [ "$$i" -le $$nargs ]; do
       |         eval arg=$bs$${$$i}
       |         case $$arg in
       |             -J-*) set -- "$$@" "$${arg#-J}" ;;
       |         esac
       |         i=$$((i + 1))
       |     done
       |
       |set -- "$$@" -jar "$jarPath"
       |
       |i=1; while [ "$$i" -le $$nargs ]; do
       |         eval arg=$bs$${$$i}
       |         case $$arg in
       |             -J-*) ;;
       |             *) set -- "$$@" "$$arg" ;;
       |         esac
       |         i=$$((i + 1))
       |     done
       |
       |shift "$$nargs"
       |""".stripMargin
  }

}
