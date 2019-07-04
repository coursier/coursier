package coursier.maven

import coursier.core.{Module, Organization, Repository}
import coursier.util.{Monad, WebPage}

final case class MavenComplete[F[_]](
  repo: MavenRepository,
  fetch: Repository.Fetch[F],
  F: Monad[F]
) extends Repository.Complete[F] {

  private def fromDirListing(dirUrl: String, prefix: String): F[Either[Throwable, Seq[String]]] =
    F.map(fetch(repo.artifactFor(dirUrl, changing = true)).run) {
      case Left(e) =>
        Left(new Exception(e))
      case Right(rawListing) =>
        val entries = WebPage.listDirectories(dirUrl, rawListing)
        Right(entries.filter(_.startsWith(prefix)))
    }

  def organization(prefix: String): F[Either[Throwable, Seq[String]]] = {

    val idx = prefix.lastIndexOf('.')
    val (base, dir, prefix0) =
      if (idx < 0)
        ("", Nil, prefix)
      else
        (prefix.take(idx + 1), prefix.take(idx).split('.').toSeq, prefix.drop(idx + 1))

    val dirUrl = repo.urlFor(dir, isDir = true)

    F.map(fromDirListing(dirUrl, prefix0))(_.right.map(_.map(base + _)))
  }
  def moduleName(organization: Organization, prefix: String): F[Either[Throwable, Seq[String]]] = {

    val dir = organization.value.split('.').toSeq
    val dirUrl = repo.urlFor(dir, isDir = true)

    fromDirListing(dirUrl, prefix)
  }
  def versions(module: Module, prefix: String): F[Either[Throwable, Seq[String]]] =
    F.map(repo.versions(module, fetch)(F).run) {
      case Left(e) =>
        Left(new Exception(e))
      case Right(v) =>
        Right(v.available.filter(_.startsWith(prefix)))
    }
}
