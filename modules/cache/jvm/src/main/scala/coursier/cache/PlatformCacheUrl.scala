package coursier.cache

import java.net.{
  Authenticator,
  PasswordAuthentication,
  URLStreamHandler,
  URLStreamHandlerFactory
}
import java.util.concurrent.ConcurrentHashMap

import coursier.cache.net.{Proxy, URL, URLConnection, HttpURLConnection}
import coursier.core.Authentication

import scala.util.control.NonFatal

/** The parts of [[CacheUrl]] that are JVM-specific: `URLStreamHandler`-based custom protocols, and
  * the JVM-wide proxy `Authenticator`.
  */
abstract class PlatformCacheUrl {

  private val handlerClsCache = new ConcurrentHashMap[String, Option[URLStreamHandler]]

  private def handlerFor(url: String, classLoaders: Seq[ClassLoader]): Option[URLStreamHandler] = {
    val protocol = url.takeWhile(_ != ':')

    // Extra class loaders can provide protocol handlers that a previous lookup without
    // them would have missed. Skip the protocol-only cache in that case.
    if (classLoaders.isEmpty)
      Option(handlerClsCache.get(protocol)) match {
        case Some(handlerOpt) => handlerOpt
        case None =>
          val handlerOpt = lookupHandler(protocol, classLoaders)
          val prevOpt    = Option(handlerClsCache.putIfAbsent(protocol, handlerOpt))
          prevOpt.getOrElse(handlerOpt)
      }
    else
      lookupHandler(protocol, classLoaders).orElse(
        Option(handlerClsCache.get(protocol)).flatten
      )
  }

  private def lookupHandler(
    protocol: String,
    classLoaders: Seq[ClassLoader]
  ): Option[URLStreamHandler] = {
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
        .find(_ => true)
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

    handlerFactoryOpt.flatMap {
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

  private[cache] def openConnection(
    url0: String,
    classLoaders: Seq[ClassLoader],
    proxyOpt: Option[Proxy]
  ): URLConnection = {
    val jnUrl = url(url0, classLoaders)
    proxyOpt match {
      case None        => jnUrl.openConnection()
      case Some(proxy) => jnUrl.openConnection(proxy)
    }
  }

  private[cache] def applyAuthentication(conn: URLConnection, auth: Authentication): Unit =
    conn match {
      case authenticated: AuthenticatedURLConnection =>
        authenticated.authenticate(auth)
      case conn0: HttpURLConnection =>
        for ((k, v) <- auth.allHttpHeaders)
          conn0.setRequestProperty(k, v)
      case _ =>
      // FIXME Authentication is ignored
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
    // Allow Basic auth for proxy tunnels (disabled by default by the JDK) only if we have
    // proxy credentials
    if (
      credentials.nonEmpty && System.getProperty("jdk.http.auth.tunneling.disabledSchemes") == null
    )
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
