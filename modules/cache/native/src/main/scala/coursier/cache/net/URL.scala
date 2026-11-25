package coursier.cache.net

import java.net.{MalformedURLException, URI}

/** Scala Native counterpart of `java.net.URL`, restricted to what the cache needs: the protocols it
  * can actually talk (`http`, `https` and `file`), and enough of the shape of the JVM class for the
  * shared code to be written once.
  */
final class URL private (val getProtocol: String, val rawUrl: String) {

  private lazy val uri: URI =
    try new URI(rawUrl)
    catch {
      case e: java.net.URISyntaxException =>
        throw new MalformedURLException(s"Malformed URL $rawUrl (${e.getMessage})")
    }

  def getHost: String = uri.getHost
  def getPort: Int    = uri.getPort
  def getPath: String = uri.getPath
  def getFile: String = {
    val q = uri.getRawQuery
    val p = Option(uri.getRawPath).getOrElse("")
    if (q == null) p else s"$p?$q"
  }

  def openConnection(): URLConnection =
    openConnection(null)

  def openConnection(proxy: Proxy): URLConnection =
    getProtocol match {
      case "http" | "https" => new HttpConnection(this, Option(proxy))
      case "file"           => new FileConnection(this)
      case other =>
        throw new MalformedURLException(s"unknown protocol: $other")
    }

  override def toString: String = rawUrl
  override def hashCode: Int    = rawUrl.hashCode
  override def equals(other: Any): Boolean = other match {
    case u: URL => u.rawUrl == rawUrl
    case _      => false
  }
}

object URL {

  private val supported = Set("http", "https", "file")

  def apply(s: String): URL = {
    val idx = s.indexOf(':')
    if (idx <= 0)
      throw new MalformedURLException(s"no protocol: $s")
    val protocol = s.take(idx).toLowerCase(java.util.Locale.ROOT)
    if (!supported.contains(protocol))
      throw new MalformedURLException(s"unknown protocol: $protocol")
    new URL(protocol, s)
  }

}
