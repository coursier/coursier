package coursier.launcher

import java.io.InputStream
import java.nio.charset.{Charset, StandardCharsets}

import dataclass._

import scala.io.{Codec, Source}

@data class Preamble(
  kind: Preamble.Kind = Preamble.Kind.Sh,
  javaOpts: Seq[String] = Nil,
  jarPath: Option[String] = None,
  command: Option[String] = None,
  extraEnv: Map[String, String] = Map.empty,
  @since
  jvmOptionFile: Option[String] = None
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

  def addExtraEnvVar(key: String, value: String): Preamble =
    withExtraEnv(extraEnv + (key -> value))

  def sh: Array[Byte] = {
    val setVars = extraEnv
      .toVector
      .sorted
      // escaping possibly a bit loose :-|
      .map { case (k, v) => s"""export $k="$v"""" }

    val lines = command match {
      case None =>
        val setJavaCmd =
          """[ -x "$JAVA_HOME/bin/java" ] && JAVA_CMD="$JAVA_HOME/bin/java" || JAVA_CMD=java"""

        val javaCmd = Seq("$JAVA_CMD") ++
          // escaping possibly a bit loose :-|
          javaOpts.map(s => "'" + s.replace("'", "\\'") + "'") ++
          jvmOptionFile.toSeq.map(_ => "${extra_jvm_opts[@]}") ++
          Seq("$JAVA_OPTS", "\"$@\"")

        val sh = jvmOptionFile.fold("sh")(_ => "bash")

        Seq(s"#!/usr/bin/env $sh") ++
          setVars ++
          Seq(Preamble.shArgsPartitioner(jarPath.getOrElse("$0"))) ++
          jvmOptionFile.toSeq.map(f => Preamble.bashJvmOptFile(f)) ++
          Seq(setJavaCmd) ++
          Seq("exec " + javaCmd.mkString(" "))

      case Some(c) =>
        Seq("#!/usr/bin/env sh") ++
          setVars ++
          Seq(
            "exec \"" + c + "\" \"$@\""
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

    val content0 = content.replace(
      "@EXTRA_VARS@",
      extraEnv
        .toVector
        .sorted
        // FIXME no escaping :|
        .map { case (k, v) => s"set $k=$v\r\n" }
        .mkString
    )

    content0.getBytes(Charset.defaultCharset())
  }

  def value: Array[Byte] =
    kind match {
      case Preamble.Kind.Sh  => sh
      case Preamble.Kind.Bat => bat
    }
}

object Preamble {

  sealed abstract class Kind extends Product with Serializable
  object Kind {
    case object Sh  extends Kind
    case object Bat extends Kind
  }

  private def readResource(path: String): String = {

    var is: InputStream = null

    try {
      is = getClass
        .getClassLoader
        .getResourceAsStream(path)
      Source.fromInputStream(is)(Codec.UTF8).mkString
    }
    finally if (is != null)
      is.close()
  }

  private lazy val batJarTemplate: String =
    readResource("coursier/launcher/jar-launcher.bat")
  private lazy val batCommandTemplate: String =
    readResource("coursier/launcher/launcher.bat")

  private def shArgsPartitioner(jarPath: String): String = {
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

  // adapted from https://github.com/paulp/sbt-extras/blob/fa06c268993aa72fc094dce06a71182827aad395/sbt#L486-L493
  // and https://github.com/paulp/sbt-extras/blob/fa06c268993aa72fc094dce06a71182827aad395/sbt#L589
  private def bashJvmOptFile(fileName: String): String =
    s"""jvm_opts_file="$fileName"
       |
       |# skip #-styled comments and blank lines
       |readConfigFile() {
       |  local end=false
       |  until $$end; do
       |    read -r || end=true
       |    [[ $$REPLY =~ ^# ]] || [[ -z $$REPLY ]] || echo "$$REPLY"
       |  done <"$$1"
       |}
       |
       |if [ -f "$$jvm_opts_file" ]; then
       |  while read -r opt; do extra_jvm_opts+=("$$opt"); done < <(readConfigFile "$$jvm_opts_file")
       |fi
       |""".stripMargin

}
