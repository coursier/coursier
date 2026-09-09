package coursier.cache

import java.io.IOException
import java.net.{
  InetAddress,
  InetSocketAddress,
  Proxy,
  ProxySelector,
  Socket,
  SocketAddress,
  SocketException,
  SocketTimeoutException,
  URL,
  URLConnection
}
import javax.net.ssl.{HttpsURLConnection, SSLSocketFactory}

import scala.concurrent.duration.FiniteDuration
import scala.util.control.NonFatal

/** Retries a request against the other addresses a host resolves to
  *
  * `HttpURLConnection` connects via `new InetSocketAddress(host, port)`, which keeps only the first
  * address `InetAddress.getAllByName` returns. So a host whose first address happens to be
  * unreachable fails for good: the JDK caches the resolution, and plainly retrying the request hits
  * that same address. That covers one node of a load-balanced mirror being down, an AAAA record on
  * a machine with no IPv6 route, or an address only reachable from another network in a
  * split-horizon setup.
  *
  * The retries here keep the original URL, and only redirect the TCP connection to a chosen
  * address. That matters: the `Host` header, SNI, certificate verification, the credentials that
  * were matched against the host, and redirection targets all stay exactly what they would have
  * been. Rewriting the URL to hold the address instead gets all of these wrong, and a
  * virtual-hosted repository - anything behind a CDN, or a reverse proxy - would answer such a
  * request from the wrong virtual host: a 404 that resolution reads as "this repository doesn't
  * have it".
  *
  * How the connection is redirected depends on the protocol:
  *   - https: [[PinnedAddressSslSocketFactory]] hands `HttpsClient` a socket that connects where we
  *     want it to
  *   - http: the address is passed as an HTTP proxy, which makes the JDK connect there and send an
  *     absolute request URI, `Host` header included
  */
private[cache] object AddressFallback {

  /** Runs `args`, then tries the other addresses of its host if it failed to connect */
  def connectionMaybePartial(
    args: CacheUrl.Args,
    perIpConnectTimeout: Option[FiniteDuration],
    resolve: String => Seq[InetAddress] = defaultResolve
  ): (URLConnection, Boolean) = {

    val initialEx =
      try return CacheUrl.urlConnectionMaybePartial(args)
      catch {
        case e: IOException if isConnectionFailure(e) => e
      }

    var res: (URLConnection, Boolean) = null

    for {
      target    <- target(args)
      addresses <- otherAddresses(target, resolve)
    } {
      val it = addresses.iterator
      while (res == null && it.hasNext) {
        val address = it.next()
        for (pinnedArgs <- pinned(args, target, address, perIpConnectTimeout))
          try {
            val attempt = CacheUrl.urlConnectionMaybePartial(pinnedArgs)
            if (isFaithful(attempt._1, target))
              res = attempt
            else
              CacheUrl.closeConn(attempt._1)
          }
          catch {
            case NonFatal(e) =>
              // this address didn't work out either: keep the failure around for the report,
              // and move on to the next one
              initialEx.addSuppressed(e)
          }
      }
    }

    if (res == null)
      // the failures of the addresses that were tried hang off it, as suppressed exceptions
      throw initialEx
    else
      res
  }

  /** Whether `t` is the kind of failure another address may not run into
    *
    * `SocketException` covers `ConnectException` ("Connection refused"), `NoRouteToHostException`,
    * and the plain "Network is unreachable" a machine with no route to an IPv6 address gets.
    * `SocketTimeoutException` covers a host that answers nothing, and a load balancer that accepts
    * the connection but never answers.
    *
    * These all come out of opening the connection and reading the response head:
    * [[CacheUrl.urlConnectionMaybePartial]] returns before the body is read.
    */
  def isConnectionFailure(t: Throwable): Boolean = t match {
    case _: SocketException        => true
    case _: SocketTimeoutException => true
    case _                         => false
  }

  private def defaultResolve(host: String): Seq[InetAddress] =
    try InetAddress.getAllByName(host).toSeq
    catch {
      case NonFatal(_) => Nil
    }

  /** What the request was aiming at, when that is something we can retry elsewhere */
  private def target(args: CacheUrl.Args): Option[Target] =
    // a connection through a proxy fails on the proxy's address, which has nothing to do with the
    // addresses the host resolves to
    if (args.proxyOpt.nonEmpty) None
    else
      for {
        url <- scala.util.Try(CacheUrl.url(args.url0, args.classLoaders)).toOption
        if !usesProxy(url)
        protocol = url.getProtocol.toLowerCase(java.util.Locale.ROOT)
        if protocol == "http" || protocol == "https"
        host <- Option(url.getHost) if host.nonEmpty
        port = if (url.getPort == -1) url.getDefaultPort else url.getPort
        if port > 0
      } yield Target(protocol, host, port)

  /** The addresses of `target` worth trying, most promising first
    *
    * The JDK connects to the first address of the list, so that one goes last here: it is the one
    * that just failed. It is kept rather than dropped in case the list got resolved again in a
    * different order in between - the JDK caches resolutions, but for a while only, and the attempt
    * that failed may well have taken longer than that to time out.
    *
    * `None` when there is nothing else to try, a literal address among others: it resolves to
    * itself, and gives a single-element list here.
    */
  private def otherAddresses(
    target: Target,
    resolve: String => Seq[InetAddress]
  ): Option[Seq[InetAddress]] = {
    val addresses = resolve(target.host)
    if (addresses.lengthCompare(1) > 0) Some(addresses.drop(1) ++ addresses.take(1))
    else None
  }

  /** Whether the JVM is set up to reach `url` through a proxy
    *
    * Covers the `http.proxyHost` & co Java properties, and any `ProxySelector` the application
    * installed.
    */
  private def usesProxy(url: URL): Boolean =
    try
      Option(ProxySelector.getDefault).exists { selector =>
        val proxies = selector.select(url.toURI)
        proxies != null && {
          val it    = proxies.iterator()
          var found = false
          while (!found && it.hasNext)
            found = it.next().`type`() != Proxy.Type.DIRECT
          found
        }
      }
    catch {
      case NonFatal(_) => false
    }

  /** `args`, tweaked so that the connection it opens goes to `address` */
  private def pinned(
    args: CacheUrl.Args,
    target: Target,
    address: InetAddress,
    perIpConnectTimeout: Option[FiniteDuration]
  ): Option[CacheUrl.Args] =
    target.protocol match {
      case "https" =>
        val underlying = args.sslSocketFactoryOpt
          .getOrElse(HttpsURLConnection.getDefaultSSLSocketFactory)
        Some(
          args.copy(
            sslSocketFactoryOpt =
              Some(new PinnedAddressSslSocketFactory(underlying, target.host, address)),
            connectTimeout = perIpConnectTimeout
          )
        )
      case "http" =>
        // an HTTP proxy is handed the URL as it stands, so the request keeps its Host header, and
        // only its destination changes
        Some(
          args.copy(
            proxyOpt = Some(
              new Proxy(Proxy.Type.HTTP, new InetSocketAddress(address, target.port))
            ),
            connectTimeout = perIpConnectTimeout
          )
        )
      case _ =>
        None
    }

  /** Whether `conn` answers the request we meant to send
    *
    * The https attempts are pinned per host, so a redirection elsewhere connects on its own, and
    * whatever comes back is sound. The http ones are pinned for the whole attempt, so a redirection
    * to another host or port would have gone to the wrong server, and its answer has to go. A 400
    * goes too: that is what a server refusing the absolute request URI an HTTP proxy sends - which
    * RFC 7230 requires it to accept - answers.
    */
  private def isFaithful(conn: URLConnection, target: Target): Boolean =
    target.protocol != "http" || {
      val url = conn.getURL
      url.getProtocol.equalsIgnoreCase("http") &&
      url.getHost.equalsIgnoreCase(target.host) &&
      (if (url.getPort == -1) url.getDefaultPort else url.getPort) == target.port &&
      CacheUrl.responseCode(conn).forall(_ != 400)
    }

  private final case class Target(protocol: String, host: String, port: Int)

  /** Creates sockets that connect to `address`, rather than to whatever `host` resolves to
    *
    * `HttpsClient` asks the SSL socket factory for an unconnected socket, connects it itself, then
    * hands it back to the factory to be wrapped with SSL - passing the host name from the URL. Our
    * socket lands in between: it redirects the connection, and leaves the SSL layer, SNI and
    * certificate verification included, to the factory we wrap, none the wiser.
    *
    * Only connections to `host` are redirected, so that a redirection to another host, which
    * `HttpsClient` would ask this same factory for, still goes where it should.
    */
  private[cache] final class PinnedAddressSslSocketFactory(
    underlying: SSLSocketFactory,
    host: String,
    address: InetAddress
  ) extends SSLSocketFactory {

    override def createSocket(): Socket =
      new Socket {
        override def connect(endpoint: SocketAddress, timeout: Int): Unit =
          endpoint match {
            case inet: InetSocketAddress if host.equalsIgnoreCase(inet.getHostString) =>
              super.connect(new InetSocketAddress(address, inet.getPort), timeout)
            case _ =>
              super.connect(endpoint, timeout)
          }
      }

    override def createSocket(s: Socket, host: String, port: Int, autoClose: Boolean): Socket =
      underlying.createSocket(s, host, port, autoClose)
    override def getDefaultCipherSuites: Array[String]   = underlying.getDefaultCipherSuites
    override def getSupportedCipherSuites: Array[String] = underlying.getSupportedCipherSuites
    override def createSocket(host: String, port: Int): Socket =
      underlying.createSocket(host, port)
    override def createSocket(
      host: String,
      port: Int,
      localHost: InetAddress,
      localPort: Int
    ): Socket =
      underlying.createSocket(host, port, localHost, localPort)
    override def createSocket(host: InetAddress, port: Int): Socket =
      underlying.createSocket(host, port)
    override def createSocket(
      address: InetAddress,
      port: Int,
      localAddress: InetAddress,
      localPort: Int
    ): Socket =
      underlying.createSocket(address, port, localAddress, localPort)
  }

}
