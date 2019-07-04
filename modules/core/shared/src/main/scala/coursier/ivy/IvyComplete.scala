package coursier.ivy

import coursier.core.{Module, Organization, Repository}
import coursier.util.Monad

final case class IvyComplete[F[_]](
  repo: IvyRepository,
  fetch: Repository.Fetch[F],
  F: Monad[F]
) extends Repository.Complete[F] {
  private implicit def F0 = F


  private lazy val organizationListingPatternOpt: Option[Pattern] =
    repo.patternUpTo(Pattern.Chunk.Var("organisation"))
  private lazy val nameListingPatternOpt: Option[Pattern] =
    repo.patternUpTo(Pattern.Chunk.Var("module"))


  def organization(prefix: String): F[Either[Throwable, Seq[String]]] =
    F.map(repo.listing(
      organizationListingPatternOpt,
      "organizations",
      Map.empty,
      fetch
    ).run) {
      case Left(e) => Left(new Exception(e))
      case Right(None) => Left(new Exception(s"Can't list organizations of ${repo.metadataPattern.string}"))
      case Right(Some((_, l))) => Right(l.filter(_.startsWith(prefix)))
    }

  def moduleName(organization: Organization, prefix: String): F[Either[Throwable, Seq[String]]] =
    F.map(repo.listing(
      nameListingPatternOpt,
      "module names",
      repo.orgVariables(organization),
      fetch
    ).run) {
      case Left(e) => Left(new Exception(e))
      case Right(None) => Left(new Exception(s"Can't list module names of ${repo.metadataPattern.string}"))
      case Right(Some((_, l))) => Right(l.filter(_.startsWith(prefix)))
    }

  def versions(module: Module, prefix: String): F[Either[Throwable, Seq[String]]] =
    F.map(repo.availableVersions(module, fetch).run) {
      case Left(e) => Left(new Exception(e))
      case Right(None) => Left(new Exception("Version listing not available on this repository"))
      case Right(Some((_, l))) => Right(l.map(_.repr).filter(_.startsWith(prefix)))
    }
}
