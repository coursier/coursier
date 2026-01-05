package coursier

import coursier.cache.CacheEnv
import coursier.core.Repository
import coursier.params.{Mirror, MirrorConfFile}
import coursier.parse.RepositoryParser
import coursier.util.{EnvEntry, EnvValues}

import java.nio.file.Path

import scala.cli.config.{ConfigDb, Keys}

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

      RepositoryParser.repositories(l).either match {
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

    def default = Seq(
      LocalRepositories.ivy2Local,
      Repositories.central
    )

    fromEnvOpt
      .orElse(fromPropsOpt)
      .orElse(fromConfFiles)
      .getOrElse(default)
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
}
