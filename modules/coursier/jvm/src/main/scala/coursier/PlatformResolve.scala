package coursier

import java.io.File

import scala.cli.config.{ConfigDb, Keys}

import coursier.params.{Mirror, MirrorConfFile}
import coursier.parse.RepositoryParser
import coursier.paths.CoursierPaths
import coursier.proxy.SetupProxy

abstract class PlatformResolve {

  type Path = java.nio.file.Path

  def defaultConfFiles: Seq[Path] =
    Seq(coursier.paths.CoursierPaths.scalaConfigFile())
  def defaultMirrorConfFiles: Seq[MirrorConfFile] = {
    val files = coursier.paths.Mirror.defaultConfigFiles().toSeq ++
      Option(coursier.paths.Mirror.extraConfigFile()).toSeq
    files.map { f =>
      // Warn if f has group and others read permissions?
      MirrorConfFile(f.getAbsolutePath, optional = true)
    }
  }

  def confFileMirrors(confFile: Path): Seq[Mirror] = {
    val db       = ConfigDb.open(confFile).fold(e => throw new Exception(e), identity)
    val valueOpt = db.get(Keys.repositoriesMirrors).fold(e => throw new Exception(e), identity)
    valueOpt.toList.flatten.map { input =>
      Mirror.parse(input) match {
        case Left(err) => throw new Exception(s"Malformed mirror: $err")
        case Right(m)  => m
      }
    }
  }

  def confFileRepositories(confFile: Path): Option[Seq[Repository]] = {
    val db       = ConfigDb.open(confFile).fold(e => throw new Exception(e), identity)
    val valueOpt = db.get(Keys.defaultRepositories).fold(e => throw new Exception(e), identity)
    valueOpt.map { inputs =>
      RepositoryParser.repositories(inputs).either match {
        case Left(errors) => ???
        case Right(repos) => repos
      }
    }
  }

  lazy val defaultRepositories: Seq[Repository] = {

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

    val fromEnvOpt = Option(System.getenv("COURSIER_REPOSITORIES"))
      .map(_.trim)
      .filter(_.nonEmpty)
      .flatMap(fromString(_, "environment variable COURSIER_REPOSITORIES"))

    val fromPropsOpt = sys.props
      .get("coursier.repositories")
      .map(_.trim)
      .filter(_.nonEmpty)
      .flatMap(fromString(_, "Java property coursier.repositories"))

    val default = Seq(
      LocalRepositories.ivy2Local,
      Repositories.central
    )

    fromEnvOpt
      .orElse(fromPropsOpt)
      .getOrElse(default)
  }

  def proxySetup(): Unit =
    if (!SetupProxy.setup()) {
      val configPath = CoursierPaths.scalaConfigFile()
      val db = ConfigDb.open(configPath)
        .fold(e => throw new Exception(e), identity)
      val addrOpt     = db.get(Keys.proxyAddress).fold(e => throw new Exception(e), identity)
      val userOpt     = db.get(Keys.proxyUser).fold(e => throw new Exception(e), identity)
      val passwordOpt = db.get(Keys.proxyPassword).fold(e => throw new Exception(e), identity)

      for (addr <- addrOpt) {
        val userOrNull     = userOpt.map(_.get().value).orNull
        val passwordOrNull = passwordOpt.map(_.get().value).orNull
        SetupProxy.setProxyProperties(addr, userOrNull, passwordOrNull, "")
        SetupProxy.setupAuthenticator()
      }
    }

}
