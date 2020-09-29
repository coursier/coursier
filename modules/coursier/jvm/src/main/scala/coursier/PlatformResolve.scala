package coursier

import java.io.File

import coursier.params.MirrorConfFile
import coursier.parse.RepositoryParser

abstract class PlatformResolve {

  def defaultMirrorConfFiles: Seq[MirrorConfFile] = {
    val files = coursier.paths.Mirror.defaultConfigFiles().toSeq ++
      Option(coursier.paths.Mirror.extraConfigFile()).toSeq
    files.map { f =>
      // Warn if f has group and others read permissions?
      MirrorConfFile(f.getAbsolutePath, optional = true)
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

}
