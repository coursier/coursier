package coursier.cli.scaladex

import argonaut.Argonaut._
import coursier.util.{EitherT, Gather}

final case class ScaladexWebServiceImpl[F[_]](fetch: String => EitherT[F, String, String], G: Gather[F]) extends ScaladexWebService[F] {

  // quick & dirty API for querying scaladex

  def search(name: String, target: String, scalaVersion: String): EitherT[F, String, Seq[ScaladexWebService.SearchResult]] = {

    val s = fetch(
      // FIXME Escaping
      s"https://index.scala-lang.org/api/search?q=$name&target=$target&scalaVersion=$scalaVersion"
    )

    s.flatMap(s => EitherT.fromEither(s.decodeEither[List[ScaladexWebService.SearchResult]]))
  }

  /**
    *
    * @param organization: GitHub organization
    * @param repository: GitHub repository name
    * @param artifactName: Scaladex artifact name
    * @return
    */
  def artifactInfos(organization: String, repository: String, artifactName: String): EitherT[F, String, ScaladexWebService.ArtifactInfos] = {

    val s = fetch(
      // FIXME Escaping
      s"https://index.scala-lang.org/api/project?organization=$organization&repository=$repository&artifact=$artifactName"
    )

    s.flatMap(s => EitherT.fromEither(s.decodeEither[ScaladexWebService.ArtifactInfos]))
  }

  /**
    *
    * @param organization: GitHub organization
    * @param repository: GitHub repository name
    * @return
    */
  def artifactNames(organization: String, repository: String): EitherT[F, String, Seq[String]] = {

    val s = fetch(
      // FIXME Escaping
      s"https://index.scala-lang.org/api/project?organization=$organization&repository=$repository"
    )

    import argonaut.ArgonautShapeless._

    final case class Result(artifacts: List[String])

    s.flatMap(s => EitherT.fromEither(s.decodeEither[Result].map(_.artifacts)))
  }


}
