package coursier.cache

import coursier.util.EnvValues
import utest._

import java.io.{ByteArrayOutputStream, IOException}
import java.net.{InetAddress, InetSocketAddress, Socket, URLConnection}
import java.nio.charset.StandardCharsets.UTF_8

import scala.concurrent.duration.DurationInt

/** The multi-address fallback of [[AddressFallback]].
  *
  * The addresses a host resolves to are injected, rather than left to DNS: a test that needs a name
  * with one reachable address and one unreachable one, in that order, cannot get one out of the
  * machine it runs on.
  *
  * What these assert on is not only that the fallback connects, but that the request it ends up
  * sending is the one that was meant - `Host` header included. An implementation redirecting the
  * URL to the address instead of the connection also connects, and then gets served by the wrong
  * virtual host.
  */
object AddressFallbackTests extends TestSuite {

  private val body = "address fallback".getBytes(UTF_8)

  /** Nothing listens there, and nothing is meant to
    *
    * How connecting to it fails is up to the OS: a connection refused where the whole of
    * 127.0.0.0/8 is local, a timeout on macOS, where only 127.0.0.1 is. The timeouts below are kept
    * short for the latter, and the assertions stay off the exact exception.
    */
  private def unreachable  = InetAddress.getByName("127.0.0.9")
  private def unreachable2 = InetAddress.getByName("127.0.0.8")
  private def reachable    = InetAddress.getByName("127.0.0.1")

  private def args(url: String) = CacheUrl.Args(
    url,
    url,
    None,
    0L,
    followHttpToHttpsRedirections = false,
    followHttpsToHttpRedirections = false,
    Nil,
    None,
    None,
    None,
    "GET",
    None,
    redirectionCount = 0,
    Some(20),
    Nil,
    connectTimeout = Some(1.second),
    readTimeout = Some(5.seconds)
  )

  private def connect(url: String, addresses: Seq[InetAddress]): (URLConnection, Boolean) =
    AddressFallback.connectionMaybePartial(
      args(url),
      Some(1.second),
      _ => addresses
    )

  private def content(conn: URLConnection): String = {
    val is  = conn.getInputStream
    val out = new ByteArrayOutputStream
    try {
      val buf   = Array.ofDim[Byte](16384)
      var nRead = is.read(buf)
      while (nRead != -1) {
        out.write(buf, 0, nRead)
        nRead = is.read(buf)
      }
    }
    finally is.close()
    new String(out.toByteArray, UTF_8)
  }

  val tests = Tests {

    test("connects to the next address when the first one is unreachable") {
      val log = new RequestLog
      RawHttpServer.withServerOn(log, "127.0.0.1")(_ => RawHttpServer.ok(body)) { server =>
        // the URL points at the address the connection fails on, the server is on another one
        val url             = s"http://${unreachable.getHostAddress}:${server.port}/dir/file.txt"
        val (conn, partial) = connect(url, Seq(unreachable, reachable))

        assert(!partial)
        assert(CacheUrl.responseCode(conn).contains(200))
        assert(content(conn) == new String(body, UTF_8))

        // the request the server got is the one that was meant for it, not one addressed to the
        // address it happens to be listening on
        val entry = log.entries.head
        assert(entry.header("Host").contains(s"${unreachable.getHostAddress}:${server.port}"))
      }
    }

    test("gives up when the host has a single address") {
      val log = new RequestLog
      RawHttpServer.withServerOn(log, "127.0.0.1")(_ => RawHttpServer.ok(body)) { server =>
        val url = s"http://${unreachable.getHostAddress}:${server.port}/dir/file.txt"
        val ex = assertThrows[IOException] {
          connect(url, Seq(unreachable))
        }
        // nothing was tried beyond the initial attempt
        assert(ex.getSuppressed.isEmpty)
        assert(log.entries.isEmpty)
      }
    }

    test("reports the initial failure when no address works") {
      val log = new RequestLog
      RawHttpServer.withServerOn(log, "127.0.0.1")(_ => RawHttpServer.ok(body)) { server =>
        val url = s"http://${unreachable.getHostAddress}:${server.port}/dir/file.txt"
        val ex = assertThrows[IOException] {
          connect(url, Seq(unreachable, unreachable2))
        }
        // the addresses that were tried are accounted for
        assert(ex.getSuppressed.nonEmpty)
        assert(ex.getSuppressed.forall(AddressFallback.isConnectionFailure))
        assert(log.entries.isEmpty)
      }
    }

    test("PinnedAddressSslSocketFactory") {

      def connectThrough(socketHost: String, endpointHost: String, port: Int): Unit = {
        val factory = new AddressFallback.PinnedAddressSslSocketFactory(
          javax.net.ssl.HttpsURLConnection.getDefaultSSLSocketFactory,
          socketHost,
          reachable
        )
        var socket: Socket = null
        try {
          socket = factory.createSocket()
          // a host name that resolves to the address nothing listens on, like the one the JDK
          // would have handed the socket
          val endpoint = InetAddress.getByAddress(endpointHost, unreachable.getAddress)
          socket.connect(new InetSocketAddress(endpoint, port), 1000)
        }
        finally if (socket != null) socket.close()
      }

      test("redirects the connection of the host it is pinned for") {
        val log = new RequestLog
        RawHttpServer.withServerOn(log, "127.0.0.1")(_ => RawHttpServer.ok(body)) { server =>
          connectThrough("repo.example.com", "repo.example.com", server.port)
        }
      }

      test("leaves the connections of other hosts alone") {
        val log = new RequestLog
        RawHttpServer.withServerOn(log, "127.0.0.1")(_ => RawHttpServer.ok(body)) { server =>
          assertThrows[IOException] {
            connectThrough("repo.example.com", "other.example.com", server.port)
          }
        }
      }
    }

    test("env") {
      test("on unless turned off") {
        assert(CacheEnv.defaultRetryResolvedIps(EnvValues(None, None)))
        assert(CacheEnv.defaultRetryResolvedIps(EnvValues(Some("true"), None)))
        assert(!CacheEnv.defaultRetryResolvedIps(EnvValues(Some("false"), None)))
        assert(!CacheEnv.defaultRetryResolvedIps(EnvValues(None, Some("FALSE"))))
        assert(!CacheEnv.defaultRetryResolvedIps(EnvValues(None, Some("0"))))
        // the env var wins over the Java property, like the other entries of CacheEnv
        assert(CacheEnv.defaultRetryResolvedIps(EnvValues(Some("true"), Some("false"))))
      }
      test("per-IP connect timeout") {
        assert(CacheEnv.defaultPerIpConnectTimeout(EnvValues(None, None)).contains(3.seconds))
        assert(
          CacheEnv.defaultPerIpConnectTimeout(EnvValues(Some("1s"), None)).contains(1.second)
        )
        assert(
          CacheEnv.defaultPerIpConnectTimeout(EnvValues(Some("2s"), Some("40s")))
            .contains(2.seconds)
        )
        // zero means "wait as long as it takes", like it does for the other timeouts
        assert(CacheEnv.defaultPerIpConnectTimeout(EnvValues(Some("0"), None)).isEmpty)
      }
    }
  }
}
