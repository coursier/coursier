package coursier.cache.net

import java.io.{FileNotFoundException, InputStream}
import java.net.URI
import java.nio.file.{Files, Path, Paths}

/** `file:` URL support, the counterpart of the JDK's internal `sun.net.www.protocol.file` handler.
  *
  * The cache reaches this only when `localArtifactsShouldBeCached` is on - otherwise `Downloader`
  * short-circuits `file:` URLs before opening a connection.
  */
private[net] final class FileConnection(url: URL) extends URLConnection {

  private lazy val path: Path =
    try Paths.get(new URI(url.rawUrl))
    catch {
      case _: IllegalArgumentException | _: java.net.URISyntaxException =>
        Paths.get(url.rawUrl.stripPrefix("file://").stripPrefix("file:"))
    }

  def getURL: URL = url

  def setRequestProperty(key: String, value: String): Unit = ()
  def getRequestProperty(key: String): String              = null
  def setConnectTimeout(timeout: Int): Unit                = ()
  def setReadTimeout(timeout: Int): Unit                   = ()

  def connect(): Unit =
    if (!Files.exists(path))
      throw new FileNotFoundException(path.toString)

  def getInputStream: InputStream = {
    connect()
    Files.newInputStream(path)
  }

  def getHeaderField(name: String): String = null

  def getContentLengthLong: Long =
    if (Files.exists(path)) Files.size(path) else -1L

  def getLastModified: Long =
    if (Files.exists(path)) Files.getLastModifiedTime(path).toMillis else 0L

  def getContentEncoding: String = null
}
