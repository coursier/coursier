package coursier.cli.scaladex

import java.net.HttpURLConnection
import java.nio.charset.StandardCharsets
import java.util.concurrent.ExecutorService

import coursier.cache.CacheUrl
import coursier.core.Artifact
import coursier.util.{EitherT, Gather, Task}
import coursier.{Fetch, Module}

trait Scaladex[F[_]] {
  /**
    * Modules / versions known to the Scaladex
    *
    * Latest version only.
    */
  def dependencies(name: String, scalaVersion: String, logger: String => Unit): EitherT[F, String, Seq[(Module, String)]]
}

object Scaladex {

  def apply(pool: ExecutorService): Scaladex[Task] =
    ScaladexWebServiceImpl({ url =>
      EitherT(Task.schedule[Either[String, String]](pool) {
        var conn: HttpURLConnection = null

        val b = try {
          conn = new java.net.URL(url).openConnection().asInstanceOf[HttpURLConnection]
          coursier.internal.FileUtil.readFullyUnsafe(conn.getInputStream)
        } finally {
          if (conn != null)
            CacheUrl.closeConn(conn)
        }

        Right(new String(b, StandardCharsets.UTF_8))
      })
    }, Gather[Task])

  def withCache(fetch: Fetch.Content[Task]): Scaladex[Task] =
    ScaladexWebServiceImpl({
      url =>
        def get(fetch: Fetch.Content[Task]) =
          fetch(
            Artifact(url, Map(), Map(), changing = true, optional = false, None)
          )

        get(fetch)
    }, Gather[Task])

}
