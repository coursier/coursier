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

  def urlConnection(url0: String, authentication: Option[Authentication]) = {
    var conn: URLConnection = null

    try {
      conn = url(url0).openConnection() // FIXME Should this be closed?

      conn match {
        case conn0: HttpURLConnection =>
          // Dummy user-agent instead of the default "Java/...",
          // so that we are not returned incomplete/erroneous metadata
          // (Maven 2 compatibility? - happens for snapshot versioning metadata)
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

      conn
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
