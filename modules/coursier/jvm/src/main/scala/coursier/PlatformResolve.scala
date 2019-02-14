package coursier

import coursier.cache.{CacheParse, LocalRepositories}
import coursier.util.Repositories

abstract class PlatformResolve {

  lazy val defaultRepositories: Seq[Repository] = {

    def fromString(str: String, origin: String): Option[Seq[Repository]] = {

      val l = str
        .split('|')
        .toSeq
        .filter(_.nonEmpty)

      CacheParse.repositories(l).either match {
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

    val fromEnvOpt = sys.env
      .get("COURSIER_REPOSITORIES")
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
