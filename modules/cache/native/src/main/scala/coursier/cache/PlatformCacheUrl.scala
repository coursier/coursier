package coursier.cache

import coursier.cache.net.{HttpURLConnection, Proxy, URL, URLConnection}
import coursier.core.Authentication

/** Scala Native counterpart of the JVM [[PlatformCacheUrl]].
  *
  * Custom protocol handlers rely on `Class.forName` and `URLStreamHandlerFactory`, neither of which
  * exists here, so `classLoaders` is ignored and only the protocols [[coursier.cache.net.URL]]
  * knows about are available. Same for the JVM-wide proxy `Authenticator`.
  */
abstract class PlatformCacheUrl {

  def url(s: String, classLoaders: Seq[ClassLoader]): URL =
    URL(s)

  def url(s: String): URL =
    URL(s)

  private[cache] def openConnection(
    url0: String,
    classLoaders: Seq[ClassLoader],
    proxyOpt: Option[Proxy]
  ): URLConnection = {
    val u = url(url0, classLoaders)
    proxyOpt match {
      case None        => u.openConnection()
      case Some(proxy) => u.openConnection(proxy)
    }
  }

  private[cache] def applyAuthentication(conn: URLConnection, auth: Authentication): Unit =
    conn match {
      case conn0: HttpURLConnection =>
        for ((k, v) <- auth.allHttpHeaders)
          conn0.setRequestProperty(k, v)
      case _ =>
      // FIXME Authentication is ignored
    }

  def disableProxyAuth(): Unit = ()

}
