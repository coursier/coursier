package redirectingserver

import cats.effect.IO
import cats.effect.unsafe.implicits.global // ???
import org.http4s._
import org.http4s.blaze.server.BlazeServerBuilder
import org.http4s.dsl.io._
import org.http4s.headers._
import org.http4s.server.Router
import scala.concurrent.ExecutionContext

object RedirectingServer {
  def main(args: Array[String]): Unit = {

    if (args.length > 3) {
      System.err.println(s"Usage: server host port redirect-to")
      sys.exit(1)
    }

    val host = if (args.length >= 1) args(0) else "localhost"
    val port = if (args.length >= 2) args(1).toInt else 10002

    val redirectTo =
      Uri.unsafeFromString(if (args.length >= 3) args(2) else "https://repo1.maven.org/maven2")

    def service(host: String, port: Int, redirectTo: Uri) = HttpRoutes.of[IO] {
      case GET -> Root / "health-check" =>
        Ok("Server running")
      case (method @ (GET | HEAD)) -> path =>
        println(s"${method.name} ${path.renderString}")
        TemporaryRedirect(Location(path.segments.foldLeft(redirectTo)(_ / _)))
    }

    BlazeServerBuilder[IO]
      .bindHttp(port, host)
      .withHttpApp(Router("/" -> service(host, port, redirectTo)).orNotFound)
      .resource
      .use(_ => IO.never)
      .unsafeRunSync()

    println(s"Listening on http://$host:$port")

    if (System.console() == null)
      while (true) Thread.sleep(60000L)
    else {
      println("Press Ctrl+D to exit")
      while (System.in.read() != -1) {}
    }
  }
}
