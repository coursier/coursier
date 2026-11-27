package coursier.cache.net

import java.io.{InputStream, IOException, OutputStream}

import scala.scalanative.libc.string
import scala.scalanative.posix.netdb._
import scala.scalanative.posix.netdbOps._
import scala.scalanative.posix.sys.socket
import scala.scalanative.posix.unistd
import scala.scalanative.unsafe._
import scala.scalanative.unsigned._

/** Minimal OpenSSL bindings - enough for a blocking TLS client.
  *
  * Only the handful of functions a plain `https` GET needs. All of them have been stable since
  * OpenSSL 1.1.0, and the few constants below are stable across 1.0.2, 1.1.x and 3.x.
  *
  * `libcrypto` is already a link-time dependency of this build - `java.security.MessageDigest`
  * comes from scala-native-crypto, which is backed by it - so this only adds `libssl` on top.
  */
@extern
@link("ssl")
@link("crypto")
private[net] object openssl {

  type SSL       = Ptr[Byte]
  type SSL_CTX   = Ptr[Byte]
  type SSLMethod = Ptr[Byte]

  def TLS_client_method(): SSLMethod                       = extern
  def SSL_CTX_new(method: SSLMethod): SSL_CTX              = extern
  def SSL_CTX_free(ctx: SSL_CTX): Unit                     = extern
  def SSL_CTX_set_default_verify_paths(ctx: SSL_CTX): CInt = extern

  def SSL_new(ctx: SSL_CTX): SSL                                         = extern
  def SSL_free(ssl: SSL): Unit                                           = extern
  def SSL_set_fd(ssl: SSL, fd: CInt): CInt                               = extern
  def SSL_ctrl(ssl: SSL, cmd: CInt, larg: CLong, parg: Ptr[Byte]): CLong = extern
  def SSL_set1_host(ssl: SSL, hostname: CString): CInt                   = extern
  def SSL_set_verify(ssl: SSL, mode: CInt, callback: Ptr[Byte]): Unit    = extern
  def SSL_connect(ssl: SSL): CInt                                        = extern
  def SSL_read(ssl: SSL, buf: Ptr[Byte], num: CInt): CInt                = extern
  def SSL_write(ssl: SSL, buf: Ptr[Byte], num: CInt): CInt               = extern
  def SSL_shutdown(ssl: SSL): CInt                                       = extern
  def SSL_get_error(ssl: SSL, ret: CInt): CInt                           = extern
  def SSL_get_verify_result(ssl: SSL): CLong                             = extern

  def ERR_get_error(): CUnsignedLong                              = extern
  def ERR_error_string(e: CUnsignedLong, buf: Ptr[Byte]): CString = extern
}

private[net] object Tls {

  // openssl/tls1.h
  private final val SSL_CTRL_SET_TLSEXT_HOSTNAME = 55
  private final val TLSEXT_NAMETYPE_host_name    = 0
  // openssl/ssl.h
  private final val SSL_VERIFY_PEER = 0x01
  // openssl/x509_vfy.h
  private final val X509_V_OK = 0L

  private lazy val ctx: openssl.SSL_CTX = {
    val method = openssl.TLS_client_method()
    val ctx0   = openssl.SSL_CTX_new(method)
    if (ctx0 == null)
      throw new SSLException(s"Cannot initialize TLS (${lastError()})")
    if (openssl.SSL_CTX_set_default_verify_paths(ctx0) != 1)
      throw new SSLException(s"Cannot load the system CA certificates (${lastError()})")
    ctx0
  }

  private def lastError(): String = {
    val code = openssl.ERR_get_error()
    if (code == 0.toULong) "no OpenSSL error"
    else
      Zone.acquire { implicit z =>
        val buf = alloc[Byte](256)
        fromCString(openssl.ERR_error_string(code, buf))
      }
  }

  /** Connects to `host:port` and completes a TLS handshake against `host`.
    *
    * The certificate chain is checked against the system CA store and the hostname against the
    * certificate - `SSL_set1_host` plus `SSL_VERIFY_PEER` is what makes `SSL_connect` fail rather
    * than hand back an unverified session.
    */
  def connect(host: String, port: Int, connectTimeoutMs: Int, readTimeoutMs: Int): Transport = {
    val fd = Tcp.connect(host, port, connectTimeoutMs, readTimeoutMs)
    val ssl =
      try {
        val ssl0 = openssl.SSL_new(ctx)
        if (ssl0 == null)
          throw new SSLException(s"Cannot start a TLS session (${lastError()})")
        if (openssl.SSL_set_fd(ssl0, fd) != 1) {
          openssl.SSL_free(ssl0)
          throw new SSLException(s"Cannot attach the TLS session to the socket (${lastError()})")
        }
        Zone.acquire { implicit z =>
          val hostC = toCString(host)
          // SNI - servers behind a shared address need it to pick the right certificate
          openssl.SSL_ctrl(
            ssl0,
            SSL_CTRL_SET_TLSEXT_HOSTNAME,
            TLSEXT_NAMETYPE_host_name,
            hostC.asInstanceOf[Ptr[Byte]]
          )
          openssl.SSL_set1_host(ssl0, hostC)
        }
        openssl.SSL_set_verify(ssl0, SSL_VERIFY_PEER, null)
        if (openssl.SSL_connect(ssl0) != 1) {
          val verify = openssl.SSL_get_verify_result(ssl0).toLong
          openssl.SSL_free(ssl0)
          val detail =
            if (verify != X509_V_OK) s"certificate verification failed (code $verify)"
            else lastError()
          throw new SSLException(s"TLS handshake with $host:$port failed: $detail")
        }
        ssl0
      }
      catch {
        case e: Throwable =>
          unistd.close(fd)
          throw e
      }
    new TlsTransport(fd, ssl)
  }
}

/** A socket the HTTP client can read from and write to, plain or TLS. */
private[net] trait Transport extends AutoCloseable {
  def inputStream: InputStream
  def outputStream: OutputStream
}

private[net] final class TlsTransport(fd: CInt, ssl: openssl.SSL) extends Transport {

  private var closed = false

  private def check(n: CInt, what: String): Int =
    if (n > 0) n
    else {
      val err = openssl.SSL_get_error(ssl, n)
      // 6 is SSL_ERROR_ZERO_RETURN - the peer closed the session cleanly
      if (err == 6) -1
      else if (n == 0) -1
      else throw new SSLException(s"TLS $what failed (SSL_get_error = $err)")
    }

  val inputStream: InputStream = new InputStream {
    private val one = new Array[Byte](1)
    def read(): Int = {
      val n = read(one, 0, 1)
      if (n <= 0) -1 else one(0).toInt & 0xff
    }
    override def read(b: Array[Byte], off: Int, len: Int): Int =
      if (len == 0) 0
      else {
        val bytes = b.asInstanceOf[scala.scalanative.runtime.ByteArray].at(off)
        check(openssl.SSL_read(ssl, bytes, len), "read")
      }
  }

  val outputStream: OutputStream = new OutputStream {
    private val one = new Array[Byte](1)
    def write(b: Int): Unit = {
      one(0) = b.toByte
      write(one, 0, 1)
    }
    override def write(b: Array[Byte], off: Int, len: Int): Unit = {
      var written = 0
      while (written < len) {
        val bytes = b.asInstanceOf[scala.scalanative.runtime.ByteArray].at(off + written)
        val n     = check(openssl.SSL_write(ssl, bytes, len - written), "write")
        if (n <= 0)
          throw new IOException("TLS connection closed while writing")
        written += n
      }
    }
  }

  def close(): Unit =
    if (!closed) {
      closed = true
      openssl.SSL_shutdown(ssl)
      openssl.SSL_free(ssl)
      unistd.close(fd)
      ()
    }
}

/** A blocking TCP connection, opened straight against POSIX.
  *
  * `java.net.Socket` would do for plain HTTP, but it keeps its file descriptor to itself and
  * OpenSSL needs one, so both protocols go through here rather than having two connect paths.
  */
private[net] object Tcp {

  def connect(host: String, port: Int, connectTimeoutMs: Int, readTimeoutMs: Int): CInt =
    Zone.acquire { implicit z =>
      val hints = alloc[addrinfo]()
      hints.ai_family = socket.AF_UNSPEC
      hints.ai_socktype = socket.SOCK_STREAM

      val result = alloc[Ptr[addrinfo]]()
      val status = getaddrinfo(toCString(host), toCString(port.toString), hints, result)
      if (status != 0)
        throw new java.net.UnknownHostException(s"$host (${fromCString(gai_strerror(status))})")

      var candidate = !result
      var fd        = -1
      var lastErrno = 0
      while (fd < 0 && candidate != null) {
        val s = socket.socket(candidate.ai_family, candidate.ai_socktype, candidate.ai_protocol)
        if (s >= 0)
          if (socket.connect(s, candidate.ai_addr, candidate.ai_addrlen) == 0)
            fd = s
          else {
            lastErrno = scala.scalanative.libc.errno.errno
            unistd.close(s)
          }
        else
          lastErrno = scala.scalanative.libc.errno.errno
        candidate = candidate.ai_next
      }
      freeaddrinfo(!result)

      if (fd < 0)
        throw new java.net.ConnectException(
          s"Cannot connect to $host:$port (${fromCString(string.strerror(lastErrno))})"
        )

      if (readTimeoutMs > 0)
        setTimeout(fd, readTimeoutMs)

      fd
    }

  private def setTimeout(fd: CInt, timeoutMs: Int): Unit =
    Zone.acquire { implicit z =>
      import scala.scalanative.posix.sys.time.timeval
      val tv = alloc[timeval]()
      tv._1 = (timeoutMs / 1000).toSize
      tv._2 = ((timeoutMs % 1000) * 1000).toSize
      // SOL_SOCKET / SO_RCVTIMEO
      socket.setsockopt(
        fd,
        socket.SOL_SOCKET,
        socket.SO_RCVTIMEO,
        tv.asInstanceOf[Ptr[Byte]],
        sizeof[timeval].toUInt
      )
      ()
    }

  def transport(fd: CInt): Transport = new FdTransport(fd)
}

private[net] final class FdTransport(fd: CInt) extends Transport {

  private var closed = false

  val inputStream: InputStream = new InputStream {
    private val one = new Array[Byte](1)
    def read(): Int = {
      val n = read(one, 0, 1)
      if (n <= 0) -1 else one(0).toInt & 0xff
    }
    override def read(b: Array[Byte], off: Int, len: Int): Int =
      if (len == 0) 0
      else {
        val bytes = b.asInstanceOf[scala.scalanative.runtime.ByteArray].at(off)
        val n     = unistd.read(fd, bytes, len.toUInt)
        if (n < 0)
          throw new IOException(
            fromCString(string.strerror(scala.scalanative.libc.errno.errno))
          )
        else if (n == 0) -1
        else n.toInt
      }
  }

  val outputStream: OutputStream = new OutputStream {
    private val one = new Array[Byte](1)
    def write(b: Int): Unit = {
      one(0) = b.toByte
      write(one, 0, 1)
    }
    override def write(b: Array[Byte], off: Int, len: Int): Unit = {
      var written = 0
      while (written < len) {
        val bytes = b.asInstanceOf[scala.scalanative.runtime.ByteArray].at(off + written)
        val n     = unistd.write(fd, bytes, (len - written).toUInt)
        if (n <= 0)
          throw new IOException(
            fromCString(string.strerror(scala.scalanative.libc.errno.errno))
          )
        written += n.toInt
      }
    }
  }

  def close(): Unit =
    if (!closed) {
      closed = true
      unistd.close(fd)
      ()
    }
}
