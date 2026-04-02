package coursier.cli.server

import caseapp.core.RemainingArgs
import coursier.cache.FileCache
import coursier.cache.internal.ThreadUtil
import coursier.cache.server.CacheServer
import coursier.cli.CoursierCommand
import coursier.util.{Sync, Task}
import io.undertow.Undertow
import io.undertow.server.{HttpHandler, HttpServerExchange}
import io.undertow.util.{Headers, StatusCodes}

import java.util.Base64
import java.util.concurrent.Executors
import java.util.logging.{Level, Logger}

import scala.concurrent.ExecutionContext

object Server extends CoursierCommand[ServerOptions] {

  private val basicAuthPrefix = "Basic "

  def run(options: ServerOptions, args: RemainingArgs): Unit = {

    val params = ServerParams(options).toEither match {
      case Left(errors) =>
        for (e <- errors.toList)
          System.err.println(e)
        sys.exit(1)
      case Right(p) => p
    }

    val expectedAuthOpt = params.userPassword.map {
      case (user, password) =>
        s"${user.value}:${password.value}"
    }

    val cachePool = Sync.fixedThreadPool(params.cache.parallel)
    val cache = params.cache.cache(cachePool, params.output.logger()) match {
      case fc: FileCache[Task] => fc
      case other =>
        System.err.println(s"Unexpected cache type: $other")
        sys.exit(1)
    }
    val requestsPool = Executors.newCachedThreadPool(
      ThreadUtil.daemonThreadFactory("coursier-cache-server-requests")
    )
    val baseHandler = CacheServer.handler(cache, ExecutionContext.fromExecutorService(requestsPool))
    val handler: HttpHandler = expectedAuthOpt match {
      case None => baseHandler
      case Some(expectedAuth) =>
        (exchange: HttpServerExchange) => {
          val authorized = Option(exchange.getRequestHeaders.getFirst(Headers.AUTHORIZATION))
            .filter(_.startsWith(basicAuthPrefix))
            .map(_.substring(basicAuthPrefix.length))
            .map(Base64.getDecoder.decode)
            .map(new String(_))
            .contains(expectedAuth)
          if (authorized)
            baseHandler.handleRequest(exchange)
          else {
            exchange.getResponseHeaders.put(
              Headers.WWW_AUTHENTICATE,
              "Basic realm=\"coursier cache server\""
            )
            exchange.setStatusCode(StatusCodes.UNAUTHORIZED)
            exchange.getResponseSender.send("Unauthorized")
          }
        }
    }
    Logger.getLogger("").setLevel(Level.WARNING)
    cache.logger.init()
    val server = Undertow.builder()
      .addHttpListener(params.port, params.host)
      .setHandler(handler)
      .build()
    server.start()
    System.out.println(s"Cache server started on ${params.host}:${params.port}")
  }
}
