package coursier.cache

import java.net._
import java.nio.charset.StandardCharsets
import java.util.Base64
import java.util.concurrent.ConcurrentHashMap
import java.util.Locale
import java.util.regex.Pattern

import coursier.core.Authentication
import coursier.credentials.DirectCredentials
import dataclass.data
import javax.net.ssl.{HostnameVerifier, HttpsURLConnection, SSLSocketFactory}

import scala.annotation.tailrec
import scala.util.Try
import scala.util.control.NonFatal

object CacheUrl {

  private val handlerClsCache = new ConcurrentHashMap[String, Option[URLStreamHandler]]

  private def handlerFor(url: String, classLoaders: Seq[ClassLoader]): Option[URLStreamHandler] = {
    val protocol = url.takeWhile(_ != ':')

    Option(handlerClsCache.get(protocol)) match {
      case None =>
        val clsName = List(
          "coursier",
          "cache",
          "protocol",
          s"${protocol.capitalize}Handler"
        ).mkString(".")

        def clsOpt(loader: ClassLoader): Option[Class[_]] =
          try Some(Class.forName(clsName, false, loader))
          catch {
            case _: ClassNotFoundException =>
              None
          }

        val clsOpt0: Option[Class[_]] = {
          val allLoaders = classLoaders.iterator ++ Iterator(
            Thread.currentThread().getContextClassLoader,
            getClass.getClassLoader
          )
          allLoaders
            .flatMap(clsOpt(_).iterator)
            .toStream
            .headOption
        }

        def printError(e: Exception): Unit =
          scala.Console.err.println(
            s"Cannot instantiate $clsName: " +
              e +
              Option(e.getMessage).fold("")(" (" + _ + ")")
          )

        val handlerFactoryOpt = clsOpt0.flatMap {
          cls =>
            try Some(
              cls.getDeclaredConstructor().newInstance().asInstanceOf[URLStreamHandlerFactory]
            )
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
                  s"Cannot get handler for $protocol from $clsName: " +
                    e.toString +
                    Option(e.getMessage).fold("")(" (" + _ + ")")
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

  /** Returns a `java.net.URL` for `s`, possibly using the custom protocol handlers found under the
    * `coursier.cache.protocol` namespace.
    *
    * E.g. URL `"test://abc.com/foo"`, having protocol `"test"`, can be handled by a
    * `URLStreamHandler` named `coursier.cache.protocol.TestHandler` (protocol name gets
    * capitalized, and suffixed with `Handler` to get the class name).
    *
    * @param s
    * @param classLoaders:
    *   class loaders to load custom protocol handlers from
    * @return
    */
  def url(s: String, classLoaders: Seq[ClassLoader]): URL =
    new URL(null, s, handlerFor(s, classLoaders).orNull)

  def url(s: String): URL =
    new URL(null, s, handlerFor(s, Nil).orNull)

  @deprecated("Use coursier.core.Authentication.basicAuthenticationEncode", "2.0.0-RC3")
  private[coursier] def basicAuthenticationEncode(user: String, password: String): String =
    Base64.getEncoder.encodeToString(
      s"$user:$password".getBytes(StandardCharsets.UTF_8)
    )

  private def partialContentResponseCode        = 206
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
        conn0.setRequestProperty("User-Agent", "Coursier/2.0")
        // Some remote repositories (AWS CodeArtifact) return a "false" 404 if maven-metadata.xml is requested
        // with default Accept header Java sets for HttpUrlConnection
        conn0.setRequestProperty("Accept", "*/*")

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
          for ((k, v) <- auth.allHttpHeaders)
            conn0.setRequestProperty(k, v)
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

  private def redirect(
    url: String,
    conn: URLConnection,
    followHttpToHttpsRedirections: Boolean,
    followHttpsToHttpRedirections: Boolean
  ): Option[String] =
    redirectTo(conn)
      .map { loc =>
        new URI(url).resolve(loc).toASCIIString
      }
      .filter { target =>
        val isHttp  = url.startsWith("http://")
        val isHttps = url.startsWith("https://")

        val redirToHttp  = target.startsWith("http://")
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

          val startOver = {
            val isPartial = conn0.getResponseCode == partialContentResponseCode ||
              conn0.getResponseCode == invalidPartialContentResponseCode
            isPartial && {
              val hasMatchingHeader = Option(conn0.getHeaderField("Content-Range"))
                .exists(_.startsWith(s"bytes $alreadyDownloaded-"))
              !hasMatchingHeader
            }
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

  @deprecated("Create a ConnectionBuilder() and call connection() on it instead", "2.0.0")
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
  ): URLConnection =
    ConnectionBuilder(
      url0,
      authentication,
      0L,
      followHttpToHttpsRedirections,
      followHttpsToHttpRedirections,
      credentials,
      sslSocketFactoryOpt,
      hostnameVerifierOpt,
      method,
      maxRedirectionsOpt = maxRedirectionsOpt,
      None,
      Nil
    ).connection()

  private[cache] final case class Args(
    initialUrl: String,
    url0: String,
    authentication: Option[Authentication],
    alreadyDownloaded: Long,
    followHttpToHttpsRedirections: Boolean,
    followHttpsToHttpRedirections: Boolean,
    autoCredentials: Seq[DirectCredentials],
    sslSocketFactoryOpt: Option[SSLSocketFactory],
    hostnameVerifierOpt: Option[HostnameVerifier],
    proxyOpt: Option[Proxy],
    method: String,
    authRealm: Option[String],
    redirectionCount: Int,
    maxRedirectionsOpt: Option[Int],
    classLoaders: Seq[ClassLoader]
  )

  @deprecated(
    "Create a ConnectionBuilder() and call connectionMaybePartial() on it instead",
    "2.0.0"
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
    ConnectionBuilder(
      url0,
      authentication,
      alreadyDownloaded,
      followHttpToHttpsRedirections,
      followHttpsToHttpRedirections,
      autoCredentials,
      sslSocketFactoryOpt,
      hostnameVerifierOpt,
      method,
      maxRedirectionsOpt,
      None,
      Nil
    ).connectionMaybePartial()

  @tailrec
  private[cache] def urlConnectionMaybePartial(args: Args): (URLConnection, Boolean) = {

    import args._

    var conn: URLConnection = null

    val res: Either[Args, (URLConnection, Boolean)] =
      try {
        conn = {
          val jnUrl = url(url0, args.classLoaders)
          proxyOpt match {
            case None        => jnUrl.openConnection()
            case Some(proxy) => jnUrl.openConnection(proxy)
          }
        }
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
            val redirectOpt =
              redirect(url0, conn, followHttpToHttpsRedirections, followHttpsToHttpRedirections)

            redirectOpt match {
              case Some(loc) =>
                closeConn(conn)

                if (maxRedirectionsOpt.exists(_ <= redirectionCount))
                  throw new Exception(
                    s"Too many redirections for $initialUrl (more than $redirectionCount redirections)"
                  )
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
                    .map(_.withOptional(false))
                    .orElse(
                      autoCredentials.find(_.autoMatches(url0, realmOpt)).map(_.authentication)
                    )
                  if (authentication0 == authentication && realmOpt.forall(authRealm.contains))
                    Right((conn, partialDownload))
                  else {
                    closeConn(conn)

                    if (maxRedirectionsOpt.exists(_ <= redirectionCount))
                      throw new Exception(
                        s"Too many redirections for $initialUrl (more than $redirectionCount redirections)"
                      )
                    else
                      Left(
                        args.copy(
                          authentication = authentication0,
                          authRealm = realmOpt,
                          redirectionCount = redirectionCount + 1
                        )
                      )
                  }
                }
                else
                  Right((conn, partialDownload))
            }
        }
      }
      catch {
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

  private[coursier] object BasicRealm {
    private val BasicAuthBase = (
      "(?i)" + // case-insensitive, the response might be BASIC realm=
        "^" +
        Pattern.quote("Basic") +
        " +(.*)" + // skip spaces and then take everything
        "$"
    ).r

    private val Param = (
      "\\s*(\\S+?)" + Pattern.quote("=\"") +
        "([^" + Pattern.quote("\"") + "]*)" +
        Pattern.quote("\"") +
        "\\s*" +  // skip any trailing spaces
        "(?:,|$)" // either we're at the end or we have a trailing comma
    ).r

    /* Extracting the realm from lines such as:

    Basic realm="Sonatype Nexus Repository Manager"
    Basic realm="SomeRealm", charset="UTF-8"
    BASIC charset="UTF-8", realm="SomeRealm"

    see https://tools.ietf.org/html/rfc7617#section-2.1

    Currently only "realm" and "charset" are defined, but additional challenge parameters are
    "reserved for future use".

     */

    def unapply(wwwAuthenticate: String): Option[String] =
      wwwAuthenticate match {
        case BasicAuthBase(basicAuthLine) =>
          Param.findAllMatchIn(basicAuthLine)
            .map(mobj => mobj.group(1).toLowerCase(Locale.ROOT) -> mobj.group(2))
            .collectFirst { case ("realm", realm) => realm }

        case _ =>
          None
      }
  }

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

  // seems there's no way to pass proxy authentication per connection…
  // see https://stackoverflow.com/questions/34877470/basic-proxy-authentication-for-https-urls-returns-http-1-0-407-proxy-authenticat
  @deprecated("Use coursier.proxy.SetupProxy.setup() instead", "2.1.0-M7")
  def setupProxyAuth(credentials: Map[(String, String, String), (String, String)]): Unit = {
    Authenticator.setDefault(
      new Authenticator {
        override def getPasswordAuthentication = {
          val key = (getRequestingProtocol, getRequestingHost, getRequestingPort.toString)
          credentials.get(key) match {
            case None =>
              super.getPasswordAuthentication
            case Some((user, password)) =>
              new PasswordAuthentication(user, password.toCharArray)
          }
        }
      }
    )
    if (System.getProperty("jdk.http.auth.tunneling.disabledSchemes") == null)
      System.setProperty("jdk.http.auth.tunneling.disabledSchemes", "")
  }

  @deprecated("Use coursier.proxy.SetupProxy.setup() instead", "2.1.0-M7")
  def setupProxyAuth(): Unit = {
    def authOpt(scheme: String): Option[((String, String, String), (String, String))] =
      for {
        host     <- sys.props.get(s"$scheme.proxyHost")
        port     <- sys.props.get(s"$scheme.proxyPort")
        user     <- sys.props.get(s"$scheme.proxyUser")
        password <- sys.props.get(s"$scheme.proxyPassword")
      } yield (scheme, host, port) -> (user, password)
    val httpAuthOpt  = authOpt("http")
    val httpsAuthOpt = authOpt("https")
    val map          = (httpAuthOpt.iterator ++ httpsAuthOpt.iterator).toMap
    if (map.nonEmpty)
      setupProxyAuth(map)
  }

  def disableProxyAuth(): Unit =
    Authenticator.setDefault(null)

}
