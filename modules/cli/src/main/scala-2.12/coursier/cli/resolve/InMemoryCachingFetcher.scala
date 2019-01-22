package coursier.cli.resolve

import java.util.concurrent.ConcurrentHashMap

import coursier.Fetch
import coursier.core.Artifact
import coursier.util.{EitherT, Schedulable}

/** For benchmarking purposes */
final class InMemoryCachingFetcher[F[_]](underlying: Fetch.Content[F])(implicit S: Schedulable[F]) {

  @volatile private var onlyCache0 = false
  private val cache = new ConcurrentHashMap[Artifact, Either[String, String]]
  private val byUrl = new ConcurrentHashMap[String, Either[String, String]]

  def onlyCache(): Unit = {
    onlyCache0 = true
  }

  def fromCache(url: String): String =
    Option(byUrl.get(url)) match {
      case None =>
        throw new NoSuchElementException(url)
      case Some(Left(err)) =>
        sys.error(s"$url is errored: $err")
      case Some(Right(s)) => s
    }

  def fetcher: Fetch.Content[F] =
    artifact =>
      EitherT {
        Option(cache.get(artifact)) match {
          case None =>
            if (onlyCache0)
              S.fromAttempt(Left(new NoSuchElementException(s"Artifact $artifact")))
            else
              S.map(underlying(artifact).run) { res =>
                val res0 = Option(cache.putIfAbsent(artifact, res))
                  .getOrElse(res)
                byUrl.putIfAbsent(artifact.url, res0)
                res0
              }
          case Some(res) => S.point(res)
        }
      }

}
