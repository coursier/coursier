package coursier.cache

import cats.data.{Kleisli, OptionT}
import cats.effect.IO
import org.http4s.{HttpRoutes, Request}

/** The http4s-facing side of [[RequestLog]]. */
trait PlatformRequestLog { self: RequestLog =>

  def record(req: Request[IO]): Unit =
    record(
      req.method.name,
      req.uri.path.renderString,
      req.headers.headers.map(h => (h.name.toString, h.value)).toMap
    )

}

abstract class PlatformRequestLogCompanion {

  /** Wraps `routes` so that every request reaching the server is recorded, including the ones no
    * route matches.
    */
  def logRequests(log: RequestLog)(routes: HttpRoutes[IO]): HttpRoutes[IO] =
    Kleisli { (req: Request[IO]) =>
      OptionT(IO(log.record(req)).flatMap(_ => routes.run(req).value))
    }
}
