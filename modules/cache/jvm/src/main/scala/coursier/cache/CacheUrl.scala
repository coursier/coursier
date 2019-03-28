package coursier.cache

import java.net._
import java.nio.charset.StandardCharsets
import java.util.Base64
import java.util.concurrent.ConcurrentHashMap
import java.util.regex.Pattern

import coursier.core.Authentication

import scala.util.Try
import scala.util.control.NonFatal

object CacheUrl {

  private val handlerClsCache = new ConcurrentHashMap[String, Option[URLStreamHandler]]

  private def handlerFor(url: String): Option[URLStreamHandler] = {
    val protocol = url.takeWhile(_ != ':')

    Option(handlerClsCache.get(protocol)) match {
      case None =>
        val clsName = s"coursier.cache.protocol.${protocol.capitalize}Handler"
        def clsOpt(loader: ClassLoader): Option[Class[_]] =
          try Some(Class.forName(clsName, false, loader))
          catch {
            case _: ClassNotFoundException =>
              None
          }

        val clsOpt0: Option[Class[_]] = clsOpt(Thread.currentThread().getContextClassLoader)
          .orElse(clsOpt(getClass.getClassLoader))

        def printError(e: Exception): Unit =
          scala.Console.err.println(
            s"Cannot instantiate $clsName: $e${Option(e.getMessage).fold("")(" ("+_+")")}"
          )

        val handlerFactoryOpt = clsOpt0.flatMap {
          cls =>
            try Some(cls.getDeclaredConstructor().newInstance().asInstanceOf[URLStreamHandlerFactory])
            catch {
              case e: InstantiationException =>
                printError(e)
                None
              case e: IllegalAccessException =>
                printError(e)
                None
              case e: ClassCastException =>
                printError(e)
                None
            }
        }

        val handlerOpt = handlerFactoryOpt.flatMap {
          factory =>
            try Some(factory.createURLStreamHandler(protocol))
            catch {
              case NonFatal(e) =>
                scala.Console.err.println(
                  s"Cannot get handler for $protocol from $clsName: $e${Option(e.getMessage).fold("")(" ("+_+")")}"
                )
                None
            }
        }

        val prevOpt = Option(handlerClsCache.putIfAbsent(protocol, handlerOpt))
        prevOpt.getOrElse(handlerOpt)

      case Some(handlerOpt) =>
        handlerOpt
    }
  }

  /**
    * Returns a `java.net.URL` for `s`, possibly using the custom protocol handlers found under the
    * `coursier.cache.protocol` namespace.
    *
    * E.g. URL `"test://abc.com/foo"`, having protocol `"test"`, can be handled by a
    * `URLStreamHandler` named `coursier.cache.protocol.TestHandler` (protocol name gets
    * capitalized, and suffixed with `Handler` to get the class name).
    *
    * @param s
    * @return
    */
  def url(s: String): URL =
    new URL(null, s, handlerFor(s).orNull)

  private def basicAuthenticationEncode(user: String, password: String): String =
    Base64.getEncoder.encodeToString(
      s"$user:$password".getBytes(StandardCharsets.UTF_8)
    )

  private def partialContentResponseCode = 206
  private def invalidPartialContentResponseCode = 416

  private def initialize(conn: URLConnection, authentication: Option[Authentication]): Unit = {

    conn match {
      case conn0: HttpURLConnection =>

        // handling those ourselves, so that we can update credentials upon redirection
        conn0.setInstanceFollowRedirects(false)

        // Early in the development of coursier, I ran into some repositories (Sonatype ones?) not
        // returning the same content for user agent "Java/…".
        conn0.setRequestProperty("User-Agent", "")

      case _ =>
    }

    for (auth <- authentication)
      conn match {
        case authenticated: AuthenticatedURLConnection =>
          authenticated.authenticate(auth)
        case conn0: HttpURLConnection =>
          conn0.setRequestProperty(
            "Authorization",
            "Basic " + basicAuthenticationEncode(auth.user, auth.password)
          )
        case _ =>
        // FIXME Authentication is ignored
      }
  }

  private def redirectTo(conn: URLConnection): Option[String] =
    conn match {
      case conn0: HttpURLConnection =>
        val c = conn0.getResponseCode
        if (c == 301 || c == 307 || c == 308)
          Option(conn0.getHeaderField("Location"))
        else
          None
      case _ =>
        None
    }

  private def redirect(url: String, conn: URLConnection, followHttpToHttpsRedirections: Boolean): Option[String] =
    redirectTo(conn)
      .map { loc =>
        new URI(url).resolve(loc).toASCIIString
      }
      .filter { target =>
        val isHttp = url.startsWith("http://")
        val isHttps = url.startsWith("https://")

        val redirToHttp = target.startsWith("http://")
        val redirToHttps = target.startsWith("https://")

        (isHttp && redirToHttp) ||
          (isHttps && redirToHttps) ||
          (followHttpToHttpsRedirections && isHttp && redirToHttps)
      }

  private def rangeResOpt(conn: URLConnection, alreadyDownloaded: Long): Option[Boolean] =
    if (alreadyDownloaded > 0L)
      conn match {
        case conn0: HttpURLConnection =>
          conn0.setRequestProperty("Range", s"bytes=$alreadyDownloaded-")

          val startOver = (conn0.getResponseCode == partialContentResponseCode
            || conn0.getResponseCode == invalidPartialContentResponseCode) && {
            val hasMatchingHeader = Option(conn0.getHeaderField("Content-Range"))
              .exists(_.startsWith(s"bytes $alreadyDownloaded-"))
            !hasMatchingHeader
          }

          Some(startOver)
        case _ =>
          None
      }
    else
      None

  private def is4xx(conn: URLConnection): Boolean =
    conn match {
      case conn0: HttpURLConnection =>
        val c = conn0.getResponseCode
        c / 100 == 4
      case _ =>
        false
    }


  def urlConnection(
    url0: String,
    authentication: Option[Authentication],
    followHttpToHttpsRedirections: Boolean = false
  ): URLConnection = {
    val (c, partial) = urlConnectionMaybePartial(url0, authentication, 0L, followHttpToHttpsRedirections)
    assert(!partial)
    c
  }

  def urlConnectionMaybePartial(
    url0: String,
    authentication: Option[Authentication],
    alreadyDownloaded: Long = 0L,
    followHttpToHttpsRedirections: Boolean = false
  ): (URLConnection, Boolean) = {
    var conn: URLConnection = null

    try {
      conn = url(url0).openConnection()
      initialize(conn, authentication.filter(!_.optional))

      val rangeResOpt0 = rangeResOpt(conn, alreadyDownloaded)

      rangeResOpt0 match {
        case Some(true) =>
          closeConn(conn)
          urlConnectionMaybePartial(url0, authentication, alreadyDownloaded = 0L, followHttpToHttpsRedirections)
        case _ =>
          val partialDownload = rangeResOpt0.nonEmpty
          val redirectOpt = redirect(url0, conn, followHttpToHttpsRedirections)

          redirectOpt match {
            case Some(loc) =>
              closeConn(conn)
              // TODO Try to get fresh credentials from some global preferences
              urlConnectionMaybePartial(loc, None, alreadyDownloaded, followHttpToHttpsRedirections)
            case None =>

              if (authentication.exists(_.optional) && is4xx(conn)) {
                val authentication0 = authentication.map(_.copy(optional = false))
                closeConn(conn)
                urlConnectionMaybePartial(url0, authentication0, alreadyDownloaded, followHttpToHttpsRedirections)
              } else
                (conn, partialDownload)
          }
      }
    } catch {
      case NonFatal(e) =>
        if (conn != null)
          closeConn(conn)
        throw e
    }
  }

  def responseCode(conn: URLConnection): Option[Int] =
    conn match {
      case conn0: HttpURLConnection =>
        Some(conn0.getResponseCode)
      case _ =>
        None
    }

  private val BasicRealm = (
    "^" +
      Pattern.quote("Basic realm=\"") +
      "([^" + Pattern.quote("\"") + "]*)" +
      Pattern.quote("\"") +
    "$"
  ).r

  def realm(conn: URLConnection): Option[String] =
    conn match {
      case conn0: HttpURLConnection =>
        Option(conn0.getHeaderField("WWW-Authenticate")).collect {
          case BasicRealm(realm) => realm
        }
      case _ =>
        None
    }

  private[coursier] def closeConn(conn: URLConnection): Unit = {
    Try(conn.getInputStream).toOption.filter(_ != null).foreach(_.close())
    conn match {
      case conn0: HttpURLConnection =>
        Try(conn0.getErrorStream).toOption.filter(_ != null).foreach(_.close())
        conn0.disconnect()
      case _ =>
    }
  }

}
