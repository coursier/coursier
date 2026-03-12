package coursier.cache

import coursier.credentials.{Credentials, DirectCredentials, FileCredentials, Password}
import coursier.parse.{CachePolicyParser, CredentialsParser}
import coursier.util.{EnvEntry, EnvValues}

import java.io.File
import java.net.URI
import java.nio.file.{Path, Paths}

import scala.cli.config.{ConfigDb, Keys}
import scala.concurrent.duration.{Duration, DurationInt}
import scala.util.{Failure, Success, Try}

/** Helpers meant to help compute default cache-related parameters, with the environment and Java
  * properties read from possibly non-standard locations
  *
  * The computed values then have to be passed manually to `coursier.Resolve`, `coursier.Fetch`,
  * `FileCache`, etc.
  */
object CacheEnv {

  /** Env var and Java prop names for the main cache location */
  val cache = EnvEntry("COURSIER_CACHE", "coursier.cache")

  /** Env var and Java prop names for the archive cache location */
  val archiveCache = EnvEntry("COURSIER_ARCHIVE_CACHE", "coursier.archive.cache")

  /** Env var and Java prop names for credentials */
  val credentials = EnvEntry("COURSIER_CREDENTIALS", "coursier.credentials")

  /** Env var and Java prop names for the Scala configuration file */
  val scalaCliConfig = EnvEntry("SCALA_CLI_CONFIG", "scala-cli.config")

  /** Env var and Java prop names for the cache TTL */
  val ttl = EnvEntry("COURSIER_TTL", "coursier.ttl")

  /** Env var and Java prop names for the cache policies */
  val cachePolicy = EnvEntry("COURSIER_MODE", "coursier.mode")

  /** Env var and Java prop names for the coursier configuration directory (prefer alternatives) */
  val configDir = EnvEntry("COURSIER_CONFIG_DIR", "coursier.config-dir")

  /** Computes the default main cache location from the passed env var and Java property */
  def defaultCacheLocation(values: EnvValues): Path =
    Paths.get(
      coursier.paths.CoursierPaths.computeCacheDirectoryFrom(
        values.env.orNull,
        values.prop.orNull,
        "v1"
      )
    )

  /** Computes the default archive cache location from the passed env var and Java property */
  def defaultArchiveCacheLocation(values: EnvValues): Path =
    Paths.get(
      coursier.paths.CoursierPaths.computeCacheDirectoryFrom(
        values.env.orNull,
        values.prop.orNull,
        "arc"
      )
    )

  private def isPropFile(s: String) =
    s.startsWith("/") || s.startsWith("file:")

  /** Computes the default credentials from the passed env vars and Java properties */
  def defaultCredentials(
    credentialsValues: EnvValues,
    scalaCliConfig: EnvValues,
    configDirValues: EnvValues
  ): Seq[Credentials] = {
    val credentialPropOpt = credentialsValues.env
      .orElse(credentialsValues.prop)
      .map(s => s.dropWhile(_.isSpaceChar))
    val legacyCredentials =
      if (credentialPropOpt.isEmpty) {
        // Warn if those files have group and others read permissions?
        val configDirs = coursier.paths.CoursierPaths.configDirectories(
          configDirValues.env.orNull,
          configDirValues.prop.orNull
        ).toSeq
        val mainCredentialsFiles =
          configDirs.map(configDir => new File(configDir, "credentials.properties"))
        val otherFiles = {
          // delay listing files until credentials are really needed?
          val dirs = configDirs.map(configDir => new File(configDir, "credentials"))
          val files = dirs.flatMap { dir =>
            val listOrNull = dir.listFiles { (dir, name) =>
              !name.startsWith(".") && name.endsWith(".properties")
            }
            Option(listOrNull).toSeq.flatten
          }
          Option(files).toSeq.flatten.map { f =>
            FileCredentials(f.getAbsolutePath, optional = true) // non optional?
          }
        }
        mainCredentialsFiles.map(f => FileCredentials(f.getAbsolutePath, optional = true)) ++
          otherFiles
      }
      else
        credentialPropOpt
          .toSeq
          .flatMap {
            case path if isPropFile(path) =>
              // hope Windows users can manage to use file:// URLs fine
              val path0 =
                if (path.startsWith("file:"))
                  new File(new URI(path)).getAbsolutePath
                else
                  path
              Seq(FileCredentials(path0, optional = true))
            case s =>
              CredentialsParser.parseSeq(s).either.toSeq.flatten
          }

    val configPaths       = defaultConfFiles(scalaCliConfig)
    val configCredentials = configPaths.flatMap(credentialsFromConfig)

    configCredentials ++ legacyCredentials
  }

  def credentialsFromConfig(configPath: Path): Seq[Credentials] = {
    val configDb = ConfigDb.open(configPath).fold(e => throw new Exception(e), identity)
    configDb.get(Keys.repositoryCredentials).fold(
      e => throw new Exception(e),
      _.getOrElse(Nil).map { c =>
        DirectCredentials(
          c.host,
          c.user.map(_.get().value),
          c.password.map(p => Password(p.get().value)),
          c.realm,
          c.optional.getOrElse(DirectCredentials.defaultOptional),
          c.matchHost.getOrElse(DirectCredentials.defaultMatchHost),
          // not sure about the default value here
          c.httpsOnly.getOrElse(DirectCredentials.defaultHttpsOnly),
          // not sure either about this one
          c.passOnRedirect.getOrElse(DirectCredentials.defaultPassOnRedirect)
        )
      }
    )
  }

  /** Computes the default Scala config file location from the passed env var and Java property */
  def defaultConfFiles(values: EnvValues): Seq[Path] =
    Seq(coursier.paths.CoursierPaths.scalaConfigFile(values.env.orNull, values.prop.orNull))

  /** Computes the default cache TTL from the passed env var and Java property */
  def defaultTtl(values: EnvValues): Option[Duration] = {
    val fromEnv   = values.env.flatMap(parseDuration(_).toOption)
    def fromProps = values.prop.flatMap(parseDuration(_).toOption)
    def default   = 24.hours

    fromEnv
      .orElse(fromProps)
      .orElse(Some(default))
  }

  private[coursier] def parseDuration(s: String): Either[Throwable, Duration] =
    if (s.nonEmpty && s.forall(_ == '0'))
      Right(Duration.Zero)
    else
      Try(Duration(s)) match {
        case Success(s) => Right(s)
        case Failure(t) => Left(t)
      }

  private[coursier] val noEnvCachePolicies = Seq(
    // first, try to update changing artifacts that were previously downloaded (follows TTL)
    CachePolicy.LocalUpdateChanging,
    // lastly, try to download what's missing
    CachePolicy.FetchMissing
  )

  /** Computes the default cache policies from the passed env var and Java property */
  def defaultCachePolicies(values: EnvValues): Seq[CachePolicy] = {

    def fromOption(value: Option[String], description: String): Option[Seq[CachePolicy]] =
      value.filter(_.nonEmpty).flatMap {
        str =>
          CachePolicyParser.cachePolicies(str, noEnvCachePolicies).either match {
            case Right(Seq()) =>
              Console.err.println(
                s"Warning: no mode found in $description, ignoring it."
              )
              None
            case Right(policies) =>
              Some(policies)
            case Left(_) =>
              Console.err.println(
                s"Warning: unrecognized mode in $description, ignoring it."
              )
              None
          }
      }

    val fromEnv = fromOption(
      values.env,
      s"${cachePolicy.envName} environment variable"
    )

    def fromProps = fromOption(
      values.prop,
      s"Java property ${cachePolicy.propName}"
    )

    fromEnv
      .orElse(fromProps)
      .getOrElse(noEnvCachePolicies)
  }
}
