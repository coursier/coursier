package coursier.bootstrap.launcher

import utest._

import java.io.{BufferedReader, ByteArrayOutputStream, InputStreamReader}
import java.net.{
  ConnectException,
  InetAddress,
  InetSocketAddress,
  ServerSocket,
  Socket,
  SocketException,
  URL
}
import java.nio.charset.StandardCharsets.US_ASCII

/** The multi-address fallback of [[Download]].
  *
  * `coursier.cache.AddressFallback` does the same for the downloads coursier itself runs, and its
  * tests say more about why. The launcher cannot share that code: it ships on its own, with nothing
  * but the JDK.
  */
object DownloadAddressFallbackTests extends TestSuite {

  /** Nothing listens there, and 127.0.0.0/8 is local, so connecting refuses at once */
  private def unreachable = InetAddress.getByName("127.0.0.9")
  private def reachable   = InetAddress.getByName("127.0.0.1")

  private val body = "launcher address fallback"

  /** A server on 127.0.0.1 that answers anything, and remembers the request headers it got */
  private def withServer[T](f: (Int, () => Map[String, String]) => T): T = {
    val server  = new ServerSocket(0, 50, reachable)
    var headers = Map.empty[String, String]
    val thread = new Thread("test-http-server") {
      override def run(): Unit =
        try
          while (true) {
            val socket = server.accept()
            try {
              val reader = new BufferedReader(new InputStreamReader(socket.getInputStream, US_ASCII))
              reader.readLine() // request line
              var line = reader.readLine()
              while (line != null && line.nonEmpty) {
                val idx = line.indexOf(':')
                if (idx > 0)
                  headers += (line.take(idx).trim -> line.drop(idx + 1).trim)
                line = reader.readLine()
              }
              val out = socket.getOutputStream
              out.write(
                s"HTTP/1.1 200 OK\r\nContent-Length: ${body.length}\r\nConnection: close\r\n\r\n$body"
                  .getBytes(US_ASCII)
              )
              out.flush()
            }
            finally socket.close()
          }
        catch {
          case _: Exception => // the server got closed
        }
    }
    thread.setDaemon(true)
    thread.start()
    try f(server.getLocalPort, () => headers)
    finally server.close()
  }

  private def read(conn: java.net.URLConnection): String = {
    val is  = conn.getInputStream
    val out = new ByteArrayOutputStream
    try {
      val buf   = Array.ofDim[Byte](4096)
      var nRead = is.read(buf)
      while (nRead != -1) {
        out.write(buf, 0, nRead)
        nRead = is.read(buf)
      }
    }
    finally is.close()
    new String(out.toByteArray, US_ASCII)
  }

  val tests = Tests {

    test("an http connection pinned to an address keeps the URL it was given") {
      withServer { (port, headers) =>
        // the URL points at the address nothing listens on, the connection goes to the other one
        val url  = new URL(s"http://${unreachable.getHostAddress}:$port/dir/file.txt")
        val conn = Download.openConnection(url, reachable, 2000)
        assert(read(conn) == body)
        // the server got the request that was meant for it, not one addressed to the address it
        // happens to be listening on
        assert(headers().get("Host").contains(s"${unreachable.getHostAddress}:$port"))
      }
    }

    test("PinnedAddressSslSocketFactory") {

      def connectThrough(pinnedHost: String, endpointHost: String, port: Int): Unit = {
        val factory = new Download.PinnedAddressSslSocketFactory(
          javax.net.ssl.HttpsURLConnection.getDefaultSSLSocketFactory,
          pinnedHost,
          reachable
        )
        var socket: Socket = null
        try {
          socket = factory.createSocket()
          // a host name that resolves to the address nothing listens on, like the one the JDK
          // would have handed the socket
          val endpoint = InetAddress.getByAddress(endpointHost, unreachable.getAddress)
          socket.connect(new InetSocketAddress(endpoint, port), 2000)
        }
        finally if (socket != null) socket.close()
      }

      test("redirects the connection of the host it is pinned for") {
        withServer { (port, _) =>
          connectThrough("repo.example.com", "repo.example.com", port)
        }
      }

      test("leaves the connections of other hosts alone") {
        withServer { (port, _) =>
          assertThrows[ConnectException] {
            connectThrough("repo.example.com", "other.example.com", port)
          }
        }
      }
    }

    test("connection failures") {
      assert(Download.isConnectionFailure(new ConnectException("refused")))
      assert(Download.isConnectionFailure(new SocketException("Network is unreachable")))
      assert(Download.isConnectionFailure(new java.net.SocketTimeoutException("timed out")))
      assert(!Download.isConnectionFailure(new java.io.FileNotFoundException("nope")))
    }

    test("nothing to fall back on") {
      // a literal address resolves to itself
      assert(Download.otherAddresses(new URL("http://127.0.0.9:8080/x")) == null)
      // a protocol whose connection we have no way to redirect
      assert(Download.otherAddresses(new URL("file:/tmp/x")) == null)
    }

    test("per-IP connect timeout") {
      def perIpConnectTimeout(value: String): Int =
        withProp("coursier.per-ip-connect-timeout", value)(Download.perIpConnectTimeout())
      assert(perIpConnectTimeout(null) == 3000)
      assert(perIpConnectTimeout("5s") == 5000)
      assert(perIpConnectTimeout("250ms") == 250)
      assert(perIpConnectTimeout("2") == 2000)
      // an unusable value falls back on the default rather than failing the download
      assert(perIpConnectTimeout("later") == 3000)
    }

    test("turned off") {
      withProp("coursier.retry-resolved-ips", "false") {
        assert(Download.otherAddresses(new URL("http://localhost:8080/x")) == null)
      }
    }
  }

  private def withProp[T](name: String, value: String)(f: => T): T = {
    val previous = System.getProperty(name)
    if (value == null) System.clearProperty(name) else System.setProperty(name, value)
    try f
    finally
      if (previous == null) System.clearProperty(name) else System.setProperty(name, previous)
  }
}
