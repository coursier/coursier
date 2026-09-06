package coursier.cache

import cats.data.{Kleisli, OptionT}
import cats.effect.IO
import org.http4s.{HttpRoutes, Request}

import java.util.concurrent.ConcurrentLinkedQueue

import scala.jdk.CollectionConverters._

/** Records the requests a test HTTP server receives.
  *
  * Cache tests should assert on the requests that were issued, not only on the content that ended
  * up in the cache: a resume that silently re-downloads from scratch, or a lock that lets two
  * downloads through, both give byte-perfect content.
  */
final class RequestLog {

  import RequestLog.Entry

  private val buf = new ConcurrentLinkedQueue[Entry]

  def record(method: String, path: String, headers: Map[String, String]): Unit = {
    buf.add(Entry(method, path, headers))
    ()
  }

  def record(req: Request[IO]): Unit =
    record(
      req.method.name,
      req.uri.path.renderString,
      req.headers.headers.map(h => (h.name.toString, h.value)).toMap
    )

  def entries: List[Entry] = buf.iterator().asScala.toList

  def methods: List[String] = entries.map(_.method)

  def methodsAndPaths: List[(String, String)] = entries.map(e => (e.method, e.path))

  def count(method: String): Int = methods.count(_ == method)

  def reset(): Unit = buf.clear()

  override def toString: String =
    entries.mkString("RequestLog(", ", ", ")")
}

object RequestLog {

  final case class Entry(method: String, path: String, headers: Map[String, String]) {
    def header(name: String): Option[String] =
      headers.collectFirst {
        case (k, v) if k.equalsIgnoreCase(name) => v
      }
    def range: Option[String]           = header("Range")
    def ifModifiedSince: Option[String] = header("If-Modified-Since")
  }

  /** Wraps `routes` so that every request reaching the server is recorded, including the ones no
    * route matches.
    */
  def logRequests(log: RequestLog)(routes: HttpRoutes[IO]): HttpRoutes[IO] =
    Kleisli { (req: Request[IO]) =>
      OptionT(IO(log.record(req)).flatMap(_ => routes.run(req).value))
    }
}
