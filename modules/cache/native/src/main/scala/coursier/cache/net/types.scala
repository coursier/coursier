package coursier.cache.net

import java.io.{IOException, InputStream}

/** Scala Native counterparts of the `java.net` / `javax.net.ssl` types the cache is written
  * against.
  *
  * Scala Native's javalib has `java.net.Socket` but neither `java.net.URL` nor any of the
  * `URLConnection` hierarchy, and no TLS at all. The JVM module aliases these same names to the
  * `java.net` ones, so the code shared between the two platforms is written once.
  */
abstract class URLConnection {

  def getURL: URL

  def setRequestProperty(key: String, value: String): Unit
  def getRequestProperty(key: String): String

  def setConnectTimeout(timeout: Int): Unit
  def setReadTimeout(timeout: Int): Unit

  def connect(): Unit

  def getInputStream: InputStream

  def getHeaderField(name: String): String
  def getContentLengthLong: Long
  def getLastModified: Long
  def getContentEncoding: String

  override def toString: String = s"${getClass.getSimpleName}($getURL)"
}

abstract class HttpURLConnection extends URLConnection {
  def getResponseCode: Int
  def setRequestMethod(method: String): Unit
  def setInstanceFollowRedirects(followRedirects: Boolean): Unit
  def getErrorStream: InputStream
  def disconnect(): Unit
}

/** Marker for TLS-enabled connections.
  *
  * The `SSLSocketFactory` / `HostnameVerifier` knobs the JVM offers have no counterpart here - the
  * Native TLS layer is not pluggable - so setting them is a no-op.
  */
abstract class HttpsURLConnection extends HttpURLConnection {
  def setSSLSocketFactory(factory: SSLSocketFactory): Unit  = ()
  def setHostnameVerifier(verifier: HostnameVerifier): Unit = ()
}

/** Opaque on Scala Native - kept so that signatures mentioning it compile on both platforms. */
abstract class SSLSocketFactory

/** Opaque on Scala Native - kept so that signatures mentioning it compile on both platforms. */
trait HostnameVerifier

class SSLException(message: String, cause: Throwable) extends IOException(message, cause) {
  def this(message: String) = this(message, null)
}

final case class Proxy(host: String, port: Int)
