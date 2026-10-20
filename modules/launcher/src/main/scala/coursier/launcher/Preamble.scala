package coursier.launcher

import dataclass.{data, since => unroll}

import java.io.InputStream
import java.nio.charset.{Charset, StandardCharsets}

import scala.io.{Codec, Source}
import scala.util.control.NonFatal

@data(
  deprecatedSetters = true,
  deprecatedSettersMessage = "Use copy instead",
  deprecatedSettersSince = "2.1.25"
) case class Preamble(
  kind: Preamble.Kind = Preamble.Kind.Sh,
  javaOpts: Seq[String] = Nil,
  jarPath: Option[String] = None,
  command: Option[String] = None,
  extraEnv: Map[String, String] = Map.empty,
  @unroll
  jvmOptionFile: Option[String] = None
) {

  def withOsKind(isWindows: Boolean): Preamble =
    copy(kind = if (isWindows) Preamble.Kind.Bat else Preamble.Kind.Sh)

  def callsItself(isWindows: Boolean): Preamble = {
    val updatedJarPath =
      if (isWindows) "%~dpnx0"
      else "$0"
    withJarPath(updatedJarPath)
  }

  def withJarPath(path: String): Preamble =
    copy(jarPath = Some(path))
  def withCommand(command: String): Preamble =
    copy(command = Some(command))

  def addExtraEnvVar(key: String, value: String): Preamble =
    copy(extraEnv = extraEnv + (key -> value))

  /** The sh preamble.
    *
    * `javaOpts`, `extraEnv` values and `jvmOptionFile` are written so that the script passes them
    * verbatim. `jarPath` and `command` are shell snippets on the other hand, put as is between
    * double quotes (so that they can reference things like `$0`): callers need to escape whatever
    * they interpolate in them, with [[Preamble.shDoubleQuoteEscape]].
    */
  def sh: Array[Byte] = {
    val setVars = extraEnv
      .toVector
      .sorted
      .map { case (k, v) =>
        Preamble.checkEnvVarName(k)
        s"""export $k="${Preamble.shDoubleQuoteEscape(v)}""""
      }

    val lines = command match {
      case None =>
        val setJavaCmd =
          """[ -x "$JAVA_HOME/bin/java" ] && JAVA_CMD="$JAVA_HOME/bin/java" || JAVA_CMD=java"""

        val javaCmd = Seq("\"$JAVA_CMD\"") ++
          javaOpts.map(Preamble.shSingleQuote) ++
          jvmOptionFile.toSeq.map(_ => "${extra_jvm_opts[@]}") ++
          Seq("$JAVA_OPTS", "\"$@\"")

        val sh = jvmOptionFile.fold("sh")(_ => "bash")

        Seq(s"#!/usr/bin/env $sh") ++
          setVars ++
          Seq(Preamble.shArgsPartitioner(jarPath.getOrElse("$0"))) ++
          jvmOptionFile.toSeq.map(f => Preamble.bashJvmOptFile(Preamble.shDoubleQuoteEscape(f))) ++
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

  /** The .bat preamble.
    *
    * `javaOpts` and `extraEnv` values are written so that the script passes them verbatim, and are
    * rejected if they contain characters that cannot be (`"`, line breaks, NUL). `jarPath` and
    * `command` are cmd snippets on the other hand, put as is in the script (so that they can
    * reference things like `%~dp0`): callers need to escape whatever they interpolate in them, with
    * [[Preamble.batDoubleQuoteEscape]].
    */
  def bat: Array[Byte] = {
    val content = command match {
      case None =>
        Preamble.batJarTemplate
          .replace("@JVM_OPTS@", javaOpts.map(Preamble.batArg).mkString(" "))
          .replace("@JAR_PATH@", jarPath.getOrElse("%~dpnx0"))
      case Some(c) =>
        Preamble.batCommandTemplate
          .replace("@COMMAND@", c)
    }

    val content0 = content.replace(
      "@EXTRA_VARS@",
      extraEnv
        .toVector
        .sorted
        .map { case (k, v) =>
          Preamble.checkEnvVarName(k)
          Preamble.batSetVar(k, v)
        }
        .mkString
    )

    content0.getBytes(Preamble.batCharset)
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

  /** The charset to write a .bat with.
    *
    * cmd.exe parses batch files in the console code page, which a console starts out with set to
    * the system OEM code page - neither the JVM default charset nor the ANSI code page that
    * sun.jnu.encoding reflects. Windows has to be asked at run time: in a GraalVM native image the
    * JDK's encoding properties are all frozen at build time, and none of them reports the OEM code
    * page anyway.
    *
    * Falls back to UTF-8 - when not on Windows (a .bat generated there is for some other machine,
    * whose code page we cannot know), when JNI is unavailable or turned off, and when this runtime
    * has no charset for the code page. That last one happens in a native image built without
    * -H:+AddAllCharsets, which carries UTF-8 but no legacy code page.
    */
  private lazy val batCharset: Charset = {
    val fromOs =
      if (coursier.paths.Util.useJni())
        try Option(coursier.jniutils.WindowsCodePages.oemCharset())
        catch {
          case NonFatal(_) => None
        }
      else
        None
    fromOs.getOrElse(StandardCharsets.UTF_8)
  }

  private def checkValue(value: String, forbidden: Char => Boolean, what: String): Unit =
    for (c <- value.find(forbidden))
      throw new IllegalArgumentException(
        f"Cannot write $what containing character U+${c.toInt}%04X in a launcher: $value"
      )

  private def checkEnvVarName(name: String): Unit =
    if (
      name.isEmpty || name.head.isDigit ||
      !name.forall(c => c == '_' || (c < 128 && c.isLetterOrDigit))
    )
      throw new IllegalArgumentException(
        s"Invalid environment variable name for a launcher: $name"
      )

  /** Quotes `value` as a single sh word, that sh reads back verbatim */
  private[coursier] def shSingleQuote(value: String): String = {
    checkValue(value, _ == '\u0000', "a value")
    "'" + value.replace("'", "'\\''") + "'"
  }

  /** Escapes `value`, so that sh reads it back verbatim when it's put between double quotes */
  private[coursier] def shDoubleQuoteEscape(value: String): String = {
    checkValue(value, _ == '\u0000', "a value")
    if (value.exists(shDoubleQuoteSpecial))
      value.flatMap(c => if (shDoubleQuoteSpecial(c)) "\\" + c else c.toString)
    else
      value
  }

  private def shDoubleQuoteSpecial(c: Char): Boolean =
    c == '"' || c == '$' || c == '`' || c == '\\'

  // cmd can't escape double quotes within double quotes, nor line breaks
  private def batForbidden(c: Char): Boolean =
    c == '"' || c == '\r' || c == '\n' || c == '\u0000'

  // characters that cmd interprets outside of double quotes, or that split arguments
  private def batSpecial(c: Char): Boolean =
    c == ' ' || c == '\t' || c == '&' || c == '|' || c == '<' || c == '>' || c == '^'

  /** Escapes `value`, so that cmd reads it back verbatim when it's put between double quotes in a
    * .bat file
    *
    * Throws if `value` contains characters that cannot be escaped there (`"`, line breaks, NUL).
    * Note that `!` is not escaped: it's only special if delayed expansion is enabled, which the
    * launchers don't do.
    */
  private[coursier] def batDoubleQuoteEscape(value: String): String = {
    checkValue(value, batForbidden, "a value")
    value.replace("%", "%%")
  }

  /** Writes `value` as a single command-line argument in a .bat file, as the program receives it */
  private def batArg(value: String): String = {
    val escaped = batDoubleQuoteEscape(value)
    if (value.exists(batSpecial)) {
      // backslashes before the closing quote need to be doubled, or they escape it
      val trailingBackslashes = escaped.reverseIterator.takeWhile(_ == '\\').length
      "\"" + escaped + "\\" * trailingBackslashes + "\""
    }
    else
      escaped
  }

  private def batSetVar(name: String, value: String): String = {
    val escaped = batDoubleQuoteEscape(value)
    if (value.exists(batSpecial)) "set \"" + name + "=" + escaped + "\"\r\n"
    else s"set $name=$escaped\r\n"
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
