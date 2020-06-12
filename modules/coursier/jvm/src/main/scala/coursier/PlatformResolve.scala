package coursier

import java.io.File

import coursier.params.MirrorConfFile
import coursier.parse.RepositoryParser

abstract class PlatformResolve {

  def defaultMirrorConfFiles: Seq[MirrorConfFile] = {
    val files = Seq(coursier.paths.Mirror.defaultConfigFile()) ++
      Option(coursier.paths.Mirror.extraConfigFile()).toSeq
    files.map { f =>
      // Warn if f has group and others read permissions?
      MirrorConfFile(f.getAbsolutePath, optional = true)
    }
  }

  lazy val defaultRepositories: Seq[Repository] = {

    def fromString(str: String, origin: String): Option[Seq[Repository]] = {

      val l = str
        .split('|')
        .toSeq
        .filter(_.nonEmpty)

      RepositoryParser.repositories(l).either match {
        case Left(errs) =>
          System.err.println(
            s"Ignoring $origin, error parsing repositories from it:\n" +
              errs.map("  " + _ + "\n").mkString
          )
          None
        case Right(repos) =>
          Some(repos)
      }
    }

    val fromEnvOpt = Option(System.getenv("COURSIER_REPOSITORIES"))
      .filter(_.nonEmpty)
      .flatMap(fromString(_, "environment variable COURSIER_REPOSITORIES"))

    val fromPropsOpt = sys.props
      .get("coursier.repositories")
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

}
