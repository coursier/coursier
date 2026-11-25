package coursier.cache.net

import java.io.{BufferedInputStream, ByteArrayInputStream, IOException, InputStream, OutputStream}
import java.net.URI
import java.nio.charset.StandardCharsets.{ISO_8859_1, US_ASCII}
import java.util.Locale

import scala.collection.mutable

/** A blocking HTTP/1.1 client, enough of one to back `URLConnection` on Scala Native.
  *
  * Scala Native has `java.net.Socket` but no HTTP client, so this speaks the protocol directly.
  * Notable choices:
  *   - every request sends `Connection: close`, so there is no connection pool to manage and the
  *     end of the body is unambiguous;
  *   - redirects are never followed here - coursier follows them itself, so that credentials can be
  *     re-evaluated at each hop (see `CacheUrl.urlConnectionMaybePartial`);
  *   - `getInputStream` throws on a >= 400 response, and `getErrorStream` returns the body instead,
  *     mirroring `HttpURLConnection`. The exception message is the one the JVM uses, which
  *     `Downloader.retryableHttpResponseCode` parses.
  *
  * `https` goes through OpenSSL - see [[Tls]].
  */
private[net] final class HttpConnection(url: URL, proxyOpt: Option[Proxy])
    extends HttpsURLConnection {

  private var method            = "GET"
  private val requestProperties = new mutable.LinkedHashMap[String, String]
  private var connectTimeout    = 0
  private var readTimeout       = 0

  private var transport: Transport = null
  private var connected            = false
  private var responseCode         = -1
  private var responseMessage      = ""
  private var responseHeaders      = Map.empty[String, String]
  private var body: InputStream    = null

  def getURL: URL = url

  def setRequestProperty(key: String, value: String): Unit = {
    if (connected)
      throw new IllegalStateException("Already connected")
    requestProperties += key -> value
  }

  def getRequestProperty(key: String): String =
    requestProperties.getOrElse(key, null)

  def setConnectTimeout(timeout: Int): Unit = connectTimeout = timeout
  def setReadTimeout(timeout: Int): Unit    = readTimeout = timeout

  def setRequestMethod(method0: String): Unit = {
    if (connected)
      throw new IllegalStateException("Already connected")
    method = method0
  }

  // coursier handles redirections itself, this is only ever called with false
  def setInstanceFollowRedirects(followRedirects: Boolean): Unit =
    if (followRedirects)
      throw new UnsupportedOperationException(
        "Following redirections is not supported on Scala Native"
      )

  def getResponseCode: Int = {
    connect()
    responseCode
  }

  def getHeaderField(name: String): String =
    if (name == null) {
      connect()
      s"HTTP/1.1 $responseCode $responseMessage"
    }
    else {
      connect()
      responseHeaders.getOrElse(name.toLowerCase(Locale.ROOT), null)
    }

  def getContentLengthLong: Long =
    Option(getHeaderField("Content-Length")).flatMap(s => scala.util.Try(s.trim.toLong).toOption)
      .getOrElse(-1L)

  def getLastModified: Long =
    Option(getHeaderField("Last-Modified")).flatMap(HttpConnection.parseHttpDate).getOrElse(0L)

  def getContentEncoding: String = getHeaderField("Content-Encoding")

  def getInputStream: InputStream = {
    connect()
    if (responseCode >= 400)
      throw new IOException(
        s"Server returned HTTP response code: $responseCode for URL: $url"
      )
    body
  }

  def getErrorStream: InputStream =
    if (!connected) null
    else if (responseCode >= 400) body
    else null

  def disconnect(): Unit = {
    if (body != null)
      try body.close()
      catch { case _: IOException => }
    if (transport != null)
      try transport.close()
      catch { case _: IOException => }
    body = null
    transport = null
  }

  private def isHttps = url.getProtocol == "https"

  def connect(): Unit =
    if (!connected) {
      connected = true

      val uri  = new URI(url.rawUrl)
      val host = uri.getHost
      if (host == null)
        throw new IOException(s"No host in URL $url")
      val port = if (uri.getPort > 0) uri.getPort else if (isHttps) 443 else 80

      val (connectHost, connectPort) = proxyOpt match {
        case Some(p) => (p.host, p.port)
        case None    => (host, port)
      }

      transport =
        if (isHttps) {
          if (proxyOpt.nonEmpty)
            throw new SSLException(
              s"Tunnelling https through a proxy is not supported on Scala Native (fetching $url)"
            )
          Tls.connect(connectHost, connectPort, connectTimeout, readTimeout)
        }
        else
          Tcp.transport(Tcp.connect(connectHost, connectPort, connectTimeout, readTimeout))

      // Proxies want the whole URL rather than just the path
      val requestTarget =
        if (proxyOpt.isEmpty) {
          val p = Option(uri.getRawPath).filter(_.nonEmpty).getOrElse("/")
          Option(uri.getRawQuery).fold(p)(q => s"$p?$q")
        }
        else url.rawUrl

      writeRequest(transport.outputStream, requestTarget, host, port)

      val in = new BufferedInputStream(transport.inputStream, 8192)
      readStatusLine(in)
      responseHeaders = HttpConnection.readHeaders(in)
      body =
        if (method == "HEAD" || HttpConnection.bodyless(responseCode))
          new ByteArrayInputStream(Array.emptyByteArray)
        else
          HttpConnection.bodyStream(in, responseHeaders)
    }

  private def writeRequest(out: OutputStream, target: String, host: String, port: Int): Unit = {
    val b = new StringBuilder
    b.append(method).append(' ').append(target).append(" HTTP/1.1\r\n")
    val defaultPort = if (isHttps) 443 else 80
    val hostHeader  = if (port == defaultPort) host else s"$host:$port"
    b.append("Host: ").append(hostHeader).append("\r\n")
    for ((k, v) <- requestProperties)
      b.append(k).append(": ").append(v).append("\r\n")
    // no connection pooling here - one connection per request, closed by the server
    b.append("Connection: close\r\n")
    b.append("\r\n")
    out.write(b.result().getBytes(US_ASCII))
    out.flush()
  }

  private def readStatusLine(in: InputStream): Unit = {
    val line = HttpConnection.readLine(in)
    if (line == null)
      throw new IOException(s"Empty response from $url")
    // HTTP/1.1 200 OK
    val parts = line.split(" ", 3)
    if (parts.length < 2 || !parts(0).startsWith("HTTP/"))
      throw new IOException(s"Malformed status line from $url: $line")
    responseCode =
      try parts(1).toInt
      catch {
        case _: NumberFormatException =>
          throw new IOException(s"Malformed status line from $url: $line")
      }
    responseMessage = if (parts.length > 2) parts(2) else ""
  }
}

private[coursier] object HttpConnection {

  private def bodyless(code: Int): Boolean =
    code == 204 || code == 304 || (code >= 100 && code < 200)

  /** Reads a CRLF-terminated line. Returns null at end of stream. */
  private def readLine(in: InputStream): String = {
    val b = new java.io.ByteArrayOutputStream
    var c = in.read()
    if (c < 0) null
    else {
      while (c >= 0 && c != '\n') {
        if (c != '\r')
          b.write(c)
        c = in.read()
      }
      new String(b.toByteArray, ISO_8859_1)
    }
  }

  /** Header names are lower-cased - HTTP header names are case-insensitive, and the JVM's
    * `getHeaderField` is too. Repeated headers are joined with ", ", as the JVM does.
    */
  private def readHeaders(in: InputStream): Map[String, String] = {
    var headers = Map.empty[String, String]
    var line    = readLine(in)
    while (line != null && line.nonEmpty) {
      val idx = line.indexOf(':')
      if (idx > 0) {
        val name  = line.take(idx).trim.toLowerCase(Locale.ROOT)
        val value = line.drop(idx + 1).trim
        headers += name -> headers.get(name).fold(value)(prev => s"$prev, $value")
      }
      line = readLine(in)
    }
    headers
  }

  private def bodyStream(in: InputStream, headers: Map[String, String]): InputStream =
    if (headers.get("transfer-encoding").exists(_.toLowerCase(Locale.ROOT).contains("chunked")))
      new ChunkedInputStream(in)
    else
      headers.get("content-length").flatMap(s => scala.util.Try(s.trim.toLong).toOption) match {
        case Some(len) => new LengthLimitedInputStream(in, len)
        case None      => in // read until the server closes the connection
      }

  /** Parses an HTTP header date, e.g. `Last-Modified` or `Retry-After`.
    *
    * Only the RFC 1123 form is understood - the one every server in use sends. The two obsolete
    * forms `HttpURLConnection` also accepts (RFC 850 and asctime) would need `DateTimeFormatter`
    * patterns with `EEE`/`MMM`, which resolve to empty strings unless a scala-java-locales locale
    * database is on the classpath. `RFC_1123_DATE_TIME` carries its own day and month names, so it
    * needs none of that.
    */
  def parseHttpDate(value: String): Option[Long] = {
    import java.time.Instant
    import java.time.format.DateTimeFormatter
    scala.util.Try(
      Instant.from(DateTimeFormatter.RFC_1123_DATE_TIME.parse(value.trim)).toEpochMilli
    )
      .toOption
  }

}

/** Stops at `length` bytes, so that a `Connection: close` body doesn't have to be read to EOF. */
private[net] final class LengthLimitedInputStream(underlying: InputStream, length: Long)
    extends InputStream {

  private var remaining = length

  def read(): Int =
    if (remaining <= 0L) -1
    else {
      val c = underlying.read()
      if (c >= 0) remaining -= 1
      c
    }

  override def read(b: Array[Byte], off: Int, len: Int): Int =
    if (remaining <= 0L) -1
    else {
      val toRead = math.min(len.toLong, remaining).toInt
      val read   = underlying.read(b, off, toRead)
      if (read > 0) remaining -= read
      read
    }

  override def available(): Int = math.min(remaining, underlying.available().toLong).toInt
  override def close(): Unit    = underlying.close()
}

/** Decodes `Transfer-Encoding: chunked` bodies.
  *
  * A connection that dies in the middle of a chunk surfaces as an [[IOException]], which is what
  * the resume path needs to see - a truncated body must not look like a clean end of stream.
  *
  * That error is reported one read late, though: it is held back and the read that runs into it
  * returns -1 first. Scala Native's `BufferedInputStream` keeps calling its source until the whole
  * requested length is filled or the source signals end of stream, and - unlike the JDK's, which
  * stops as soon as `available()` reaches 0 - it drops the bytes it has already copied if the
  * source throws instead. Without this, a truncated body would surface with none of the bytes that
  * did arrive, and resuming could never engage.
  */
private[net] final class ChunkedInputStream(underlying: InputStream) extends InputStream {

  private var chunkRemaining            = -1L // -1: no chunk started yet
  private var done                      = false
  private var bytesProduced             = 0L
  private var pendingError: IOException = null

  private def readChunkHeader(): Unit = {
    if (chunkRemaining == 0L)
      // trailing CRLF of the previous chunk
      expectCrLf()
    val line = readLineOrThrow()
    val size =
      try java.lang.Long.parseLong(line.takeWhile(_ != ';').trim, 16)
      catch {
        case _: NumberFormatException =>
          throw new IOException(s"Malformed chunk size: $line")
      }
    if (size == 0L) {
      done = true
      chunkRemaining = 0L
      // trailers, then the final CRLF
      var trailer = readLineOrThrow()
      while (trailer.nonEmpty)
        trailer = readLineOrThrow()
    }
    else
      chunkRemaining = size
  }

  private def expectCrLf(): Unit = {
    val c0 = underlying.read()
    if (c0 == '\r') {
      if (underlying.read() != '\n')
        throw new IOException("Malformed chunked stream")
    }
    else if (c0 != '\n')
      throw new IOException("Malformed chunked stream")
  }

  private def readLineOrThrow(): String = {
    val b = new java.io.ByteArrayOutputStream
    var c = underlying.read()
    if (c < 0)
      throw new IOException("Premature end of chunked stream")
    while (c >= 0 && c != '\n') {
      if (c != '\r')
        b.write(c)
      c = underlying.read()
    }
    if (c < 0)
      throw new IOException("Premature end of chunked stream")
    new String(b.toByteArray, ISO_8859_1)
  }

  /** Returns false once the body is over, be it cleanly or not.
    *
    * A truncation found here is stashed in `pendingError` rather than thrown, so long as some bytes
    * have already been handed out - see the class comment.
    */
  private def ensureChunk(): Boolean = {
    if (pendingError != null)
      throw pendingError
    if (!done && chunkRemaining <= 0L)
      try readChunkHeader()
      catch {
        case e: IOException if bytesProduced > 0L =>
          pendingError = e
          done = true
          chunkRemaining = 0L
      }
    !done && chunkRemaining > 0L
  }

  def read(): Int =
    if (!ensureChunk()) -1
    else {
      val c = underlying.read()
      if (c < 0)
        throw new IOException("Premature end of chunked stream")
      chunkRemaining -= 1
      bytesProduced += 1
      c
    }

  override def read(b: Array[Byte], off: Int, len: Int): Int =
    if (len == 0) 0
    else if (!ensureChunk()) -1
    else {
      val toRead = math.min(len.toLong, chunkRemaining).toInt
      val read   = underlying.read(b, off, toRead)
      if (read < 0)
        throw new IOException("Premature end of chunked stream")
      chunkRemaining -= read
      bytesProduced += read
      read
    }

  /** Bytes left in the current chunk.
    *
    * `BufferedInputStream` calls this to decide whether to keep filling a partially satisfied read.
    * Reporting 0 at a chunk boundary is what stops it from reading past the end of the body - and,
    * for a truncated response, from raising the premature-EOF error before the bytes that *did*
    * arrive have been handed back to the caller.
    */
  override def available(): Int = math.max(0L, chunkRemaining).toInt

  override def close(): Unit = underlying.close()
}
