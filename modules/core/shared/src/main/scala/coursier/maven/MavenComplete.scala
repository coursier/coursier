package coursier.maven

import coursier.core.{Module, Organization, Repository}
import coursier.util.Monad

final case class MavenComplete[F[_]](
  repo: MavenRepository,
  fetch: Repository.Fetch[F],
  F: Monad[F]
) extends Repository.Complete[F] {

  override def sbtAttrStub: Boolean =
    repo.sbtAttrStub

  private def fromDirListing(dirUrl: String, prefix: String): F[Either[Throwable, Seq[String]]] = {
    F.map(fetch(repo.artifactFor(dirUrl + ".links", changing = true)).run) {
      case Left(e) =>
        Left(new Exception(e))
      case Right(rawLinks) =>
        val entries = MavenComplete.split0(rawLinks, '\n', prefix)
        Right(entries)
    }
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
      case Right((v, _)) =>
        Right(v.available.filter(_.startsWith(prefix)))
    }
}

object MavenComplete {

  private[coursier] def split0(s: String, sep: Char, prefix: String): Vector[String] = {

    var idx = 0
    val b = Vector.newBuilder[String]

    while (idx < s.length) {
      var nextIdx = idx
      while (nextIdx < s.length && s.charAt(nextIdx) != sep)
        nextIdx += 1
      if (nextIdx - idx > prefix.length && s.regionMatches(idx, prefix, 0, prefix.length) && s.charAt(nextIdx - 1) == '/')
        b += s.substring(idx, nextIdx - 1)
      idx = nextIdx + 1
    }

    b.result()
  }

}
