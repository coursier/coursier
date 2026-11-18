package coursier.cli.config

import scala.util.control.NonFatal

import caseapp._
import scala.cli.config.{Key, Keys}

import coursier.paths.CoursierPaths

// format: off
final case class ConfigOptions(
  @Group("Config")
  @HelpMessage("Dump config DB as JSON")
  @Hidden
    dump: Boolean = false,
  @Group("Config")
  @HelpMessage("Print passwords in clear: if the entry is a password, print its value rather than how to get it, and print the passwords of credentials entries rather than masking them")
    password: Boolean = false,
  @Group("Config")
  @HelpMessage("If the entry is a password, save the password value rather than how to get the password")
    passwordValue: Boolean = false,
  @Group("Config")
  @HelpMessage("Remove an entry from config")
    unset: Boolean = false,
  @Group("Config")
  @HelpMessage("Config file path (default: Scala CLI's config file, see above)")
    configFile: Option[String] = None
) derives Parser
// format: on

object ConfigOptions {

  /** Keys read by coursier itself, listed first in the help message */
  def coursierKeys: Seq[Key[?]] = Seq(
    Keys.repositoryMirrors,
    Keys.defaultRepositories,
    Keys.repositoryCredentials,
    Keys.proxyAddress,
    Keys.proxyUser,
    Keys.proxyPassword
  )

  /** Keys accepted by the config command */
  def keys: Seq[Key[?]] = {
    val coursierKeys0 = coursierKeys
    coursierKeys0 ++ Keys.all.filter(k => !coursierKeys0.contains(k)).sortBy(_.fullName)
  }

  private def keysTable(keys: Seq[Key[?]]): String = {
    val width = keys.map(_.fullName.length).max
    keys
      .map(k => s"  ${k.fullName.padTo(width, ' ')}  ${k.description}")
      .mkString(System.lineSeparator())
  }

  /** Default location of the config file, ignoring `SCALA_CLI_CONFIG` */
  private def defaultConfigFile: String =
    try CoursierPaths.scalaConfigFile(null, null).toString
    catch {
      case NonFatal(_) =>
        "in the Scala CLI data directory"
    }

  def helpMessage: String = {
    val nl                         = System.lineSeparator()
    val (coursierKeys0, otherKeys) = keys.filter(!_.hidden).partition(coursierKeys.contains)
    s"""Print, set, or remove entries of the configuration file coursier shares with Scala CLI.
       |
       |Pass a key alone to print its value, a key and one or more values to set it,
       |or --unset and a key to remove it.
       |
       |The config file is Scala CLI's, $defaultConfigFile by default.
       |Override it with the SCALA_CLI_CONFIG environment variable, or with --config-file
       |(which takes precedence over SCALA_CLI_CONFIG).
       |As it can contain credentials, its directory must only be accessible by its owner
       |(chmod 700 <directory>) on Linux and macOS.
       |
       |Keys used by coursier:
       |${keysTable(coursierKeys0)}
       |
       |Other keys, used by Scala CLI:
       |${keysTable(otherKeys)}""".stripMargin.linesIterator.mkString(nl)
  }

  lazy val help: Help[ConfigOptions] =
    Help.derive[ConfigOptions].copy(
      argsNameOption = Some("key [value...]"), // ASCII, Windows consoles may not print "…"
      helpMessage = Some(HelpMessage(helpMessage))
    )

  given Help[ConfigOptions] = help
}
