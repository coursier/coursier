package coursier.cache.server

import com.github.plokhotnyuk.jsoniter_scala.core._
import coursier.cache.{ArtifactError, CachePolicy, FileCache}
import coursier.util.Task
import io.undertow.server.{HttpHandler, HttpServerExchange}
import io.undertow.util.{Headers, StatusCodes}

import java.util.concurrent.ConcurrentHashMap

import scala.concurrent.duration.Duration
import scala.concurrent.{ExecutionContextExecutor, Future, Promise}
import scala.util.control.NonFatal
import scala.util.{Failure, Success, Try}

object CacheServer {

  import Model._

  private def internalError(
    requestBytes: Array[Byte],
    exchange: HttpServerExchange,
    t: Throwable
  ): Unit = {
    System.err.println(
      s"Caught exception while processing ${Try(new String(requestBytes))}"
    )
    t.printStackTrace(System.err)
    exchange.getResponseHeaders.put(Headers.CONTENT_TYPE, "text/plain")
    exchange.setStatusCode(StatusCodes.INTERNAL_SERVER_ERROR)
    exchange.getResponseSender.send("Internal error")
  }

  private def parseCachePolicy(name: String): Either[String, CachePolicy] =
    Model.parseCachePolicy(name).toRight(s"Unknown cache policy '$name'")

  /** The cache policies and TTL to use for a request, defaulting to those of `cache` */
  private def policiesAndTtl(
    request: GetRequest,
    cache: FileCache[Task]
  ): Either[String, (Seq[CachePolicy], Option[Duration])] = {
    val policiesEither = request.cachePolicy match {
      case Some(name) =>
        parseCachePolicy(name).map(Seq(_))
      case None if request.cachePolicies.isEmpty =>
        Right(cache.cachePolicies)
      case None =>
        request.cachePolicies.foldLeft[Either[String, Seq[CachePolicy]]](Right(Vector.empty)) {
          (acc, name) =>
            for (l <- acc; policy <- parseCachePolicy(name)) yield l :+ policy
        }
    }
    val ttlEither = request.ttl match {
      case Some(input) => Model.parseTtl(input)
      case None        => Right(cache.ttl)
    }
    for (policies <- policiesEither; ttl <- ttlEither) yield (policies, ttl)
  }

  def handler(cache0: FileCache[Task], pool: ExecutionContextExecutor): HttpHandler = {
    // The server does the actual downloads, it mustn't defer to a cache server itself
    val cache              = cache0.copy(allowCacheSubstitution = false)
    val cachePath          = os.Path(cache.location)
    val onGoingGetRequests = new ConcurrentHashMap[String, Future[_]]

    (exchange: HttpServerExchange) =>
      if (exchange.getRequestPath == "/get" && exchange.getRequestMethod.toString == "POST")
        exchange.getRequestReceiver.receiveFullBytes {
          (exchange: HttpServerExchange, bytes: Array[Byte]) =>
            exchange.dispatch(
              pool,
              () => {
                val maybeRequest = Try(readFromArray[GetRequest](bytes))

                def proceed(whenDone: () => Unit): Unit = {
                  val maybeFutureResult = maybeRequest.map { request =>
                    val artifact = request.artifact.toArtifact
                    // System.err.println(
                    //   s"Was asked ${artifact.url}" +
                    //     request.cachePolicy.map(" (" + _ + ")").getOrElse("")
                    // )
                    val fileTask = policiesAndTtl(request, cache) match {
                      case Left(message) =>
                        Task.point[Either[ArtifactError, java.io.File]](
                          Left(new ArtifactError.DownloadError(message, None))
                        )
                      case Right((policies, ttl)) =>
                        cache.fileWith(artifact, cache.retry, policies, ttl).run
                    }
                    fileTask.future()(cache.ec)
                  }

                  maybeFutureResult match {
                    case Success(futureResult) =>
                      futureResult.onComplete { result =>
                        whenDone()
                        val response = result match {
                          case Success(Right(file)) =>
                            val relativePath = os.Path(file).subRelativeTo(cachePath).toString
                            GetResponse(
                              path = Some(relativePath),
                              error = None
                            )
                          case Success(Left(err: ArtifactError)) =>
                            GetResponse(
                              path = None,
                              error = Some(SerializedArtifactError(
                                err.`type`,
                                err.message,
                                SerializedException.fromThrowable(err)
                              ))
                            )
                          case Failure(t) =>
                            GetResponse(
                              path = None,
                              error = Some(SerializedArtifactError(
                                "exception",
                                Option(t.getMessage).getOrElse(""),
                                SerializedException.fromThrowable(t)
                              ))
                            )
                        }

                        // System.err.println(response)

                        exchange.getResponseHeaders.put(Headers.CONTENT_TYPE, "application/json")
                        exchange.getResponseSender.send(writeToString(response))
                      }(pool)
                    case Failure(t) =>
                      whenDone()
                      internalError(bytes, exchange, t)
                  }
                }

                maybeRequest match {
                  case Success(request) =>
                    val promise = Promise[Unit]()
                    def attempt(): Unit = {
                      val f = promise.future
                      Option(onGoingGetRequests.putIfAbsent(request.artifact.url, f)) match {
                        case Some(previous) =>
                          previous.onComplete { _ =>
                            attempt()
                          }(pool)
                        case None =>
                          proceed { () =>
                            val result = onGoingGetRequests.remove(request.artifact.url, f)
                            assert(result)
                            promise.success(())
                          }
                      }
                    }
                    attempt()
                  case Failure(t) =>
                    internalError(bytes, exchange, t)
                }
              }
            )
        }
      else if (exchange.getRequestPath == "/path" && exchange.getRequestMethod.toString == "POST")
        exchange.getRequestReceiver.receiveFullBytes {
          (exchange: HttpServerExchange, bytes: Array[Byte]) =>
            exchange.dispatch(
              pool,
              () => {
                val maybeFutureResult = Try {
                  val request  = readFromArray[PathRequest](bytes)
                  val artifact = request.artifact.toArtifact
                  // System.err.println(s"Was asked the path of ${artifact.url}")
                  val fileTask = cache.finalLocalPath(artifact)
                  fileTask.future()(cache.ec)
                }

                maybeFutureResult match {
                  case Success(futureResult) =>
                    futureResult.onComplete { result =>
                      val response = result match {
                        case Success(file) =>
                          // pprint.err.log(file)
                          val relativePath = os.Path(file).subRelativeTo(cachePath).toString
                          PathResponse(
                            path = Some(relativePath),
                            error = None
                          )
                        case Failure(t) =>
                          PathResponse(
                            path = None,
                            error = Some(SerializedArtifactError(
                              "exception",
                              Option(t.getMessage).getOrElse(""),
                              SerializedException.fromThrowable(t)
                            ))
                          )
                      }

                      // System.err.println(response)

                      exchange.getResponseHeaders.put(Headers.CONTENT_TYPE, "application/json")
                      exchange.getResponseSender.send(writeToString(response))
                    }(pool)
                  case Failure(t) =>
                    internalError(bytes, exchange, t)
                }
              }
            )
        }
      else
        exchange
          .setStatusCode(404)
          .getResponseSender
          .send("Not found")
  }

}
