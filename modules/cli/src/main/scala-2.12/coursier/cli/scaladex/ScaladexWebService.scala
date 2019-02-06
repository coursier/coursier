package coursier.cli.scaladex

import argonaut._
import coursier.core.{ModuleName, Organization}
import coursier.util.{EitherT, Gather}
import coursier.Module

object ScaladexWebService {

  final case class SearchResult(
    /** GitHub organization */
    organization: String,
    /** GitHub repository */
    repository: String,
    /** Scaladex artifact names */
    artifacts: List[String] = Nil
  )

  object SearchResult {
    import argonaut.ArgonautShapeless._
    implicit val decoder = DecodeJson.of[SearchResult]
  }

  final case class ArtifactInfos(
    /** Dependency group ID (aka organization) */
    groupId: String,
    /** Dependency artifact ID (aka name or module name) */
    artifactId: String,
    /** Dependency version */
    version: String
  ) {
    def organization: Organization =
      Organization(groupId)
    def name: ModuleName =
      ModuleName(artifactId)
  }

  object ArtifactInfos {
    import argonaut.ArgonautShapeless._
    implicit val decoder = DecodeJson.of[ArtifactInfos]
  }

}

trait ScaladexWebService[F[_]] extends Scaladex[F] {

  def search(
    name: String,
    target: String,
    scalaVersion: String
  ): EitherT[F, String, Seq[ScaladexWebService.SearchResult]]
  def artifactInfos(
    organization: String,
    repository: String,
    artifactName: String
  ): EitherT[F, String, ScaladexWebService.ArtifactInfos]
  def artifactNames(organization: String, repository: String): EitherT[F, String, Seq[String]]

  protected def G: Gather[F]
  protected final implicit def G0: Gather[F] = G

  final def dependencies(
    name: String,
    scalaVersion: String,
    logger: String => Unit
  ): EitherT[F, String, Seq[(Module, String)]] = {
    val idx = name.indexOf('/')
    val orgNameOrError =
      if (idx >= 0) {
        val org = name.take(idx)
        val repo = name.drop(idx + 1)

        artifactNames(org, repo).map((org, repo, _)): EitherT[F, String, (String, String, Seq[String])]
      } else
        search(name, "JVM", scalaVersion) // FIXME Don't hardcode
          .flatMap {
            case Seq(first, _*) =>
              logger(s"Using ${first.organization}/${first.repository} for $name")
              EitherT.fromEither[F](
                Right((first.organization, first.repository, first.artifacts)): Either[
                  String,
                  (String, String, Seq[String])
                ]
              )
            case Seq() =>
              EitherT.fromEither[F](Left(s"No project found for $name"): Either[String, (String, String, Seq[String])])
          }

    orgNameOrError.flatMap {
      case (ghOrg, ghRepo, artifactNames) =>
        val moduleVersions = G.map(G.gather(artifactNames.map { artifactName =>
          G.map(artifactInfos(ghOrg, ghRepo, artifactName).run) {
            case Left(err) =>
              logger(s"Cannot get infos about artifact $artifactName from $ghOrg/$ghRepo: $err, ignoring it")
              Nil
            case Right(infos) =>
              logger(s"Found module ${infos.groupId}:${infos.artifactId}:${infos.version}")
              Seq(Module(infos.organization, infos.name) -> infos.version)
          }
        }))(_.flatten)

        EitherT(G.map(moduleVersions) { l =>
          if (l.isEmpty)
            Left(s"No module found for $ghOrg/$ghRepo")
          else
            Right(l)
        })
    }
  }

}
