package coursier.cache

import java.io.{BufferedReader, InputStreamReader, OutputStream}
import java.net.{InetAddress, ServerSocket, Socket}
import java.nio.charset.StandardCharsets.US_ASCII
import java.util.concurrent.atomic.AtomicBoolean

/** A minimal HTTP server for the tests http4s cannot serve.
  *
  * Two things are needed for the resume tests and awkward to get out of a well-behaved server: a
  * response body cut short mid-flight, and byte ranges. Note that the body has to be cut short
  * *inside a chunk* - a response that announces a `Content-Length` and then sends fewer bytes is
  * accepted without complaint by `HttpURLConnection`, and never produces the partial download the
  * resume path needs.
  *
  * Requests are handled one at a time, on a single thread, which is all these tests need.
  */
final class RawHttpServer(
  log: RequestLog,
  handler: RequestLog.Entry => RawHttpServer.Response
) extends AutoCloseable {

  import RawHttpServer.Response

  private val server = new ServerSocket(0, 50, InetAddress.getByName("localhost"))
  private val closed = new AtomicBoolean(false)

  def baseUrl: String = s"http://localhost:${server.getLocalPort}"

  private val acceptThread = {
    val t = new Thread("raw-http-server") {
      override def run(): Unit =
        while (!closed.get())
          try handle(server.accept())
          catch {
            case _: Exception if closed.get() => // shutting down
          }
    }
    t.setDaemon(true)
    t.start()
    t
  }

  private def handle(socket: Socket): Unit =
    try {
      val reader      = new BufferedReader(new InputStreamReader(socket.getInputStream, US_ASCII))
      val requestLine = reader.readLine()
      if (requestLine != null) {
        val parts  = requestLine.split(" ")
        val method = parts(0)
        val path   = if (parts.length > 1) parts(1) else "/"

        var headers = Map.empty[String, String]
        var line    = reader.readLine()
        while (line != null && line.nonEmpty) {
          val idx = line.indexOf(':')
          if (idx > 0)
            headers += (line.take(idx).trim -> line.drop(idx + 1).trim)
          line = reader.readLine()
        }

        val entry = RequestLog.Entry(method, path, headers)
        log.record(method, path, headers)

        write(socket.getOutputStream, method, handler(entry))
        socket.shutdownOutput()
      }
    }
    finally socket.close()

  private def write(out: OutputStream, method: String, response: Response): Unit = {

    val head = new StringBuilder
    head.append(response.statusLine).append("\r\n")
    for ((k, v) <- response.headers)
      head.append(k).append(": ").append(v).append("\r\n")
    head.append("Connection: close\r\n\r\n")
    out.write(head.result().getBytes(US_ASCII))

    if (method != "HEAD") {
      val len = response.bodyBytesToSend.getOrElse(response.body.length)
      if (response.chunked) {
        out.write(s"${len.toHexString}\r\n".getBytes(US_ASCII))
        out.write(response.body, 0, len)
        if (response.bodyBytesToSend.isEmpty)
          out.write("\r\n0\r\n\r\n".getBytes(US_ASCII))
        // otherwise: no chunk terminator, the connection just goes away mid-chunk
      }
      else
        out.write(response.body, 0, len)
    }

    out.flush()
  }

  def close(): Unit = {
    closed.set(true)
    server.close()
    acceptThread.interrupt()
  }
}

object RawHttpServer {

  final case class Response(
    statusLine: String,
    headers: Seq[(String, String)],
    body: Array[Byte] = Array.emptyByteArray,
    /** Fewer bytes than `body` holds: the response is cut short after that many. */
    bodyBytesToSend: Option[Int] = None,
    chunked: Boolean = false
  )

  def ok(body: Array[Byte]): Response =
    Response(
      "HTTP/1.1 200 OK",
      Seq("Content-Length" -> body.length.toString),
      body
    )

  /** Announces the full length, then hangs up early.
    *
    * `HttpURLConnection` reports this as a clean end of stream, not as an error.
    */
  def truncatedFixedLength(body: Array[Byte], sendBytes: Int): Response =
    Response(
      "HTTP/1.1 200 OK",
      Seq("Content-Length" -> body.length.toString),
      body,
      bodyBytesToSend = Some(sendBytes)
    )

  /** Hangs up in the middle of a chunk, which the client reports as a premature EOF. */
  def truncatedChunked(body: Array[Byte], sendBytes: Int): Response =
    Response(
      "HTTP/1.1 200 OK",
      Seq("Transfer-Encoding" -> "chunked"),
      body,
      bodyBytesToSend = Some(sendBytes),
      chunked = true
    )

  def partialContent(body: Array[Byte], from: Int): Response =
    Response(
      "HTTP/1.1 206 Partial Content",
      Seq(
        "Content-Length" -> (body.length - from).toString,
        "Content-Range"  -> s"bytes $from-${body.length - 1}/${body.length}"
      ),
      body.drop(from)
    )

  def notFound: Response =
    Response("HTTP/1.1 404 Not Found", Seq("Content-Length" -> "0"))

  private val rangeStartRegex = "bytes=([0-9]+)-.*".r

  /** The first byte a `Range` header asks for, if any. */
  def rangeStart(entry: RequestLog.Entry): Option[Int] =
    entry.range.collect {
      case rangeStartRegex(from) => from.toInt
    }

  def withServer[T](
    log: RequestLog
  )(
    handler: RequestLog.Entry => Response
  )(
    f: String => T
  ): T = {
    val server = new RawHttpServer(log, handler)
    try f(server.baseUrl)
    finally server.close()
  }
}
