package coursier

import coursier.cache.CacheEnv
import coursier.core.Repository
import coursier.maven.MavenSettings
import coursier.params.{MavenSettingsMirror, Mirror, MirrorConfFile}
import coursier.parse.RepositoryParser
import coursier.util.{EnvEntry, EnvValues}

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path, Paths}
import java.util.Locale

import scala.cli.config.{ConfigDb, Keys}
import scala.util.control.NonFatal

/** Helpers meant to help compute default various parameters, with the environment and Java
  * properties read from possibly non-standard locations
  *
  * The computed values then have to be passed manually to `coursier.Resolve`, `coursier.Fetch`,
  * `FileCache`, etc.
  */
object CoursierEnv {

  /** Env var and Java prop names for the default repositories */
  val repositories = EnvEntry("COURSIER_REPOSITORIES", "coursier.repositories")

  /** Env var and Java prop names for the Scala configuration file */
  val scalaCliConfig = CacheEnv.scalaCliConfig

  /** Env var and Java prop names for the mirror repositories */
  val mirrors = EnvEntry("COURSIER_MIRRORS", "coursier.mirrors")

  /** Env var and Java prop names for extra mirror repositories */
  val mirrorsExtra = EnvEntry("COURSIER_EXTRA_MIRRORS", "coursier.mirrors.extra")

  /** Env var and Java prop names for the coursier configuration directory (prefer alternatives) */
  val configDir = CacheEnv.configDir

  /** Env var and Java prop names for the Maven settings file
    *
    * The value is the path of a Maven `settings.xml` file. It can also be a boolean-like value,
    * `false` disabling the reading of Maven settings altogether, and `true` keeping the default
    * lookup, based on [[mavenHome]] and [[mavenHomeFallback]].
    */
  val mavenSettings = EnvEntry("COURSIER_MAVEN_SETTINGS", "coursier.maven-settings")

  /** Env var and Java prop names for the Maven home directory, holding `settings.xml` */
  val mavenHome = EnvEntry("CS_MAVEN_HOME", "cs.maven.home")

  /** Env var and Java prop names for the Maven home directory, tried after [[mavenHome]] */
  val mavenHomeFallback = EnvEntry("MAVEN_HOME", "maven.home")

  /** Computes the default repositories from the passed env var and Java property */
  def defaultRepositories(repositories: EnvValues, scalaCliConfig: EnvValues): Seq[Repository] = {

    val spaceSep = "\\s+".r

    def fromString(str: String, origin: String): Option[Seq[Repository]] = {

      val l =
        if (spaceSep.findFirstIn(str).isEmpty)
          str
            .split('|')
            .toSeq
            .filter(_.nonEmpty)
        else
          spaceSep
            .split(str)
            .toSeq
            .filter(_.nonEmpty)

      RepositoryParser.repositories(l, Repositories.hardCodedDefaultRepositories).either match {
        case Left(errs) =>
          System.err.println(
            s"Ignoring $origin, error parsing repositories from it:" + System.lineSeparator() +
              errs.map("  " + _ + System.lineSeparator()).mkString
          )
          None
        case Right(repos) =>
          Some(repos)
      }
    }

    val fromEnvOpt = repositories.env
      .map(_.trim)
      .filter(_.nonEmpty)
      .flatMap(fromString(_, s"environment variable ${CoursierEnv.repositories.envName}"))

    def fromPropsOpt = repositories.prop
      .map(_.trim)
      .filter(_.nonEmpty)
      .flatMap(fromString(_, s"Java property ${CoursierEnv.repositories.propName}"))

    def fromConfFiles = CacheEnv.defaultConfFiles(scalaCliConfig)
      .iterator
      .flatMap(confFileRepositories(_).iterator)
      .find(_ => true)

    fromEnvOpt
      .orElse(fromPropsOpt)
      .orElse(fromConfFiles)
      .getOrElse(Repositories.hardCodedDefaultRepositories)
  }

  private[coursier] def confFileRepositories(confFile: Path): Option[Seq[Repository]] = {
    val db       = ConfigDb.open(confFile).fold(e => throw new Exception(e), identity)
    val valueOpt = db.get(Keys.defaultRepositories).fold(e => throw new Exception(e), identity)
    valueOpt.map { inputs =>
      RepositoryParser.repositories(inputs).either match {
        case Left(errors) =>
          val errorMessage = errors.mkString("Malformed repositories:\n", "\n", "")
          throw new Exception(errorMessage)
        case Right(repos) => repos
      }
    }
  }

  private[coursier] def confFileMirrors(confFile: Path): Seq[Mirror] = {
    val db       = ConfigDb.open(confFile).fold(e => throw new Exception(e), identity)
    val valueOpt = db.get(Keys.repositoriesMirrors).fold(e => throw new Exception(e), identity)
    valueOpt.toList.flatten.map { input =>
      Mirror.parse(input) match {
        case Left(err) => throw new Exception(s"Malformed mirror: $err")
        case Right(m)  => m
      }
    }
  }

  /** Computes the default mirror config file locations from the passed env var and Java property */
  def defaultMirrorConfFiles(
    values: EnvValues,
    extraValues: EnvValues,
    configDirValues: EnvValues
  ): Seq[MirrorConfFile] = {
    val configDirs = coursier.paths.CoursierPaths.configDirectories(
      configDirValues.env.orNull,
      configDirValues.prop.orNull
    )
    val files =
      coursier.paths.Mirror.defaultConfigFiles(
        values.env.orNull,
        values.prop.orNull,
        configDirs
      ).toSeq ++
        Option(coursier.paths.Mirror.extraConfigFile(
          extraValues.env.orNull,
          extraValues.prop.orNull
        )).toSeq
    files.map { f =>
      // Warn if f has group and others read permissions?
      MirrorConfFile(f.getAbsolutePath, optional = true)
    }
  }

  /** Computes the default mirror repositories from the passed env var and Java property */
  def defaultMirrors(
    mirrorValues: EnvValues,
    mirrorExtraValues: EnvValues,
    scalaCliConfig: EnvValues,
    configDirValues: EnvValues
  ): Seq[Mirror] =
    defaultMirrorConfFiles(mirrorValues, mirrorExtraValues, configDirValues).flatMap(_.mirrors()) ++
      CacheEnv.defaultConfFiles(scalaCliConfig).flatMap(confFileMirrors)

  private def firstValue(values: EnvValues): Option[String] =
    values.env.orElse(values.prop).map(_.trim).filter(_.nonEmpty)

  private val disablingValues = Set("false", "off", "0")
  private val enablingValues  = Set("true", "on", "1")

  /** Computes the default Maven settings file location from the passed env vars and Java properties
    *
    * `settings.xml` is looked for in the Maven home directories, [[mavenHome]] first, then
    * [[mavenHomeFallback]], then `~/.m2`. The first directory that actually holds a `settings.xml`
    * file wins, so that a `MAVEN_HOME` pointing at a Maven installation, as it usually does,
    * doesn't hide the settings file of the user. If none of them does, the `~/.m2` location is
    * returned.
    *
    * Returns `None` if the reading of Maven settings is disabled, see [[mavenSettings]].
    */
  def defaultMavenSettingsFile(
    mavenSettingsValues: EnvValues,
    mavenHomeValues: EnvValues,
    mavenHomeFallbackValues: EnvValues
  ): Option[Path] =
    firstValue(mavenSettingsValues).map(value => (value, value.toLowerCase(Locale.ROOT))) match {
      case Some((_, lowerCased)) if disablingValues(lowerCased) =>
        None
      case Some((value, lowerCased)) if !enablingValues(lowerCased) =>
        Some(Paths.get(value))
      case _ =>
        val candidates =
          (firstValue(mavenHomeValues).iterator ++ firstValue(mavenHomeFallbackValues).iterator)
            .map(Paths.get(_))
            .++(Option(System.getProperty("user.home")).iterator.map(Paths.get(_).resolve(".m2")))
            .map(_.resolve("settings.xml"))
            .toVector
        candidates.find(Files.isRegularFile(_)).orElse(candidates.lastOption)
    }

  /** Reads the mirrors of a Maven settings file
    *
    * Returns an empty sequence if `settingsFile` doesn't exist, and throws if it cannot be parsed.
    */
  def mavenSettingsMirrors(settingsFile: Path): Seq[Mirror] =
    if (Files.isRegularFile(settingsFile)) {
      val content = new String(Files.readAllBytes(settingsFile), StandardCharsets.UTF_8)
      MavenSettings.parse(content) match {
        case Left(error) =>
          throw new Exception(s"Error parsing $settingsFile: $error")
        case Right(settings) =>
          MavenSettingsMirror.fromSettings(settings)
      }
    }
    else
      Nil

  /** Computes the default Maven settings mirrors from the passed env vars and Java properties
    *
    * Unlike [[mavenSettingsMirrors]], a settings file that cannot be parsed is reported on the
    * standard error output and ignored, rather than making the whole resolution fail.
    */
  def defaultMavenSettingsMirrors(
    mavenSettingsValues: EnvValues,
    mavenHomeValues: EnvValues,
    mavenHomeFallbackValues: EnvValues
  ): Seq[Mirror] =
    defaultMavenSettingsFile(mavenSettingsValues, mavenHomeValues, mavenHomeFallbackValues)
      .toSeq
      .flatMap { settingsFile =>
        try mavenSettingsMirrors(settingsFile)
        catch {
          case NonFatal(e) =>
            System.err.println(
              s"Ignoring mirrors from $settingsFile: ${Option(e.getMessage).getOrElse(e.toString)}"
            )
            Nil
        }
      }
}
