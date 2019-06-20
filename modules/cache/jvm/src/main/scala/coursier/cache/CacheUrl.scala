package coursier.cache

import java.net._
import java.nio.charset.StandardCharsets
import java.util.Base64
import java.util.concurrent.ConcurrentHashMap
import java.util.regex.Pattern

import coursier.core.Authentication
import coursier.credentials.DirectCredentials
import javax.net.ssl.{HostnameVerifier, HttpsURLConnection, SSLSocketFactory}

import scala.annotation.tailrec
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

  private[coursier] def basicAuthenticationEncode(user: String, password: String): String =
    Base64.getEncoder.encodeToString(
      s"$user:$password".getBytes(StandardCharsets.UTF_8)
    )

  private def partialContentResponseCode = 206
  private def invalidPartialContentResponseCode = 416

  private def initialize(
    conn: URLConnection,
    authentication: Option[Authentication],
    sslSocketFactoryOpt: Option[SSLSocketFactory],
    hostnameVerifierOpt: Option[HostnameVerifier],
    method: String
  ): Unit = {

    conn match {
      case conn0: HttpURLConnection =>

        if (method != "GET")
          conn0.setRequestMethod(method)

        // handling those ourselves, so that we can update credentials upon redirection
        conn0.setInstanceFollowRedirects(false)

        // Early in the development of coursier, I ran into some repositories (Sonatype ones?) not
        // returning the same content for user agent "Java/…".
        conn0.setRequestProperty("User-Agent", "")

        conn0 match {
          case conn1: HttpsURLConnection =>

            for (f <- sslSocketFactoryOpt)
              conn1.setSSLSocketFactory(f)

            for (v <- hostnameVerifierOpt)
              conn1.setHostnameVerifier(v)

          case _ =>
        }

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
        if (c == 301 || c == 302 || c == 303 || c == 304 || c == 307 || c == 308)
          Option(conn0.getHeaderField("Location"))
        else
          None
      case _ =>
        None
    }

  private def redirect(url: String, conn: URLConnection, followHttpToHttpsRedirections: Boolean, followHttpsToHttpRedirections: Boolean): Option[String] =
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
          (followHttpToHttpsRedirections && isHttp && redirToHttps) ||
          (followHttpsToHttpRedirections && isHttps && redirToHttp)
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
    followHttpToHttpsRedirections: Boolean = false,
    followHttpsToHttpRedirections: Boolean = false,
    credentials: Seq[DirectCredentials] = Nil,
    sslSocketFactoryOpt: Option[SSLSocketFactory] = None,
    hostnameVerifierOpt: Option[HostnameVerifier] = None,
    method: String = "GET",
    maxRedirectionsOpt: Option[Int] = Some(20)
  ): URLConnection = {
    val (c, partial) = urlConnectionMaybePartial(
      url0,
      authentication,
      0L,
      followHttpToHttpsRedirections,
      followHttpsToHttpRedirections,
      credentials,
      sslSocketFactoryOpt,
      hostnameVerifierOpt,
      method,
      maxRedirectionsOpt = maxRedirectionsOpt
    )
    assert(!partial)
    c
  }

  private final case class Args(
    initialUrl: String,
    url0: String,
    authentication: Option[Authentication],
    alreadyDownloaded: Long,
    followHttpToHttpsRedirections: Boolean,
    followHttpsToHttpRedirections: Boolean,
    autoCredentials: Seq[DirectCredentials],
    sslSocketFactoryOpt: Option[SSLSocketFactory],
    hostnameVerifierOpt: Option[HostnameVerifier],
    method: String,
    authRealm: Option[String],
    redirectionCount: Int,
    maxRedirectionsOpt: Option[Int]
  )

  def urlConnectionMaybePartial(
    url0: String,
    authentication: Option[Authentication],
    alreadyDownloaded: Long,
    followHttpToHttpsRedirections: Boolean,
    followHttpsToHttpRedirections: Boolean,
    autoCredentials: Seq[DirectCredentials],
    sslSocketFactoryOpt: Option[SSLSocketFactory],
    hostnameVerifierOpt: Option[HostnameVerifier],
    method: String,
    maxRedirectionsOpt: Option[Int]
  ): (URLConnection, Boolean) =
    urlConnectionMaybePartial(Args(
      url0,
      url0,
      authentication,
      alreadyDownloaded,
      followHttpToHttpsRedirections,
      followHttpsToHttpRedirections,
      autoCredentials,
      sslSocketFactoryOpt,
      hostnameVerifierOpt,
      method,
      None,
      redirectionCount = 0,
      maxRedirectionsOpt
    ))

  @tailrec
  private def urlConnectionMaybePartial(args: Args): (URLConnection, Boolean) = {

    import args._

    var conn: URLConnection = null

    val res: Either[Args, (URLConnection, Boolean)] =
      try {
        conn = url(url0).openConnection()
        val authOpt = authentication.filter { a =>
          a.realmOpt.forall(authRealm.contains) &&
            !a.optional
        }
        initialize(conn, authOpt, sslSocketFactoryOpt, hostnameVerifierOpt, method)

        val rangeResOpt0 = rangeResOpt(conn, alreadyDownloaded)

        rangeResOpt0 match {
          case Some(true) =>
            closeConn(conn)
            Left(args.copy(alreadyDownloaded = 0L))
          case _ =>
            val partialDownload = rangeResOpt0.nonEmpty
            val redirectOpt = redirect(url0, conn, followHttpToHttpsRedirections, followHttpsToHttpRedirections)

            redirectOpt match {
              case Some(loc) =>
                closeConn(conn)

                if (maxRedirectionsOpt.exists(_ <= redirectionCount))
                  throw new Exception(s"Too many redirections for $initialUrl (more than $redirectionCount redirections)")
                else {
                  // should we do the reverse here (keep authentication only if we get nothing from autoCredentials)?
                  // or make that configurable?
                  val newAuthOpt = authentication
                    .filter { auth =>
                      auth.passOnRedirect &&
                        (loc.startsWith("https://") || (loc.startsWith("http://") && !auth.httpsOnly))
                    }
                    .orElse {
                      autoCredentials
                        .find(_.autoMatches(loc, None))
                        .map(_.authentication)
                    }
                  Left(
                    args.copy(
                      url0 = loc,
                      authentication = newAuthOpt,
                      authRealm = None,
                      redirectionCount = redirectionCount + 1
                    )
                  )
                }
              case None =>

                if (is4xx(conn)) {
                  val realmOpt = realm(conn)
                  val authentication0 = authentication
                    .map(_.copy(optional = false))
                    .orElse(autoCredentials.find(_.autoMatches(url0, realmOpt)).map(_.authentication))
                  if (authentication0 == authentication && realmOpt.forall(authRealm.contains))
                    Right((conn, partialDownload))
                  else {
                    closeConn(conn)

                    if (maxRedirectionsOpt.exists(_ <= redirectionCount))
                      throw new Exception(s"Too many redirections for $initialUrl (more than $redirectionCount redirections)")
                    else
                      Left(
                        args.copy(
                          authentication = authentication0,
                          authRealm = realmOpt,
                          redirectionCount = redirectionCount + 1
                        )
                      )
                  }
                } else
                  Right((conn, partialDownload))
            }
        }
      } catch {
        case NonFatal(e) =>
          if (conn != null)
            closeConn(conn)
          throw e
      }

    res match {
      case Left(newArgs) =>
        urlConnectionMaybePartial(newArgs)
      case Right(ret) =>
        ret
    }
  }

  def responseCode(conn: URLConnection): Option[Int] =
    conn match {
      case conn0: HttpURLConnection =>
        Some(conn0.getResponseCode)
      case _ =>
        None
    }

  private[coursier] val BasicRealm = (
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
