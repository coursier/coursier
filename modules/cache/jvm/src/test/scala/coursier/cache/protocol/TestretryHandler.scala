package coursier.cache.protocol

import java.io.{ByteArrayInputStream, InputStream}
import java.net.{HttpURLConnection, SocketException, URL, URLStreamHandler, URLStreamHandlerFactory}
import java.util.concurrent.atomic.AtomicInteger

object TestretryHandler {

  /** Download attempts, that is GET requests - HEAD requests are counted separately */
  val attempts: AtomicInteger = new AtomicInteger(0)

  /** Every connection opened, whatever the method
    *
    * More telling than `attempts` when what a test is after is the number of round-trips a server
    * sees, rather than the number of downloads.
    */
  val connections: AtomicInteger = new AtomicInteger(0)

  @volatile var failUntilAttempt: Option[Int] = Some(0)

  private def defaultCreateException: Int => Throwable =
    n => new SocketException(s"Simulated SocketException on attempt $n")
  var createException: Int => Throwable    = defaultCreateException
  private def defaultResponseCode          = 200
  var responseCode: Int                    = defaultResponseCode
  var responseHeaders: Map[String, String] = Map.empty

  /** Answer 429 to that many connections, then go back to `responseCode`
    *
    * A server that rate limits for a while and then lets us through, which is what most of them do
    *   - and the only way to tell "waited it out" apart from "gave up" from the outside.
    */
  @volatile var rateLimitUntilConnection: Int = 0

  def reset(failUntil: Int = -1): Unit = {
    attempts.set(0)
    connections.set(0)
    failUntilAttempt = Some(failUntil).filter(_ >= 0)
    createException = defaultCreateException
    responseCode = defaultResponseCode
    responseHeaders = Map.empty
    rateLimitUntilConnection = 0
  }
}

// Discovered by CacheUrl via classpath convention: protocol "testretry" →
// class coursier.cache.protocol.TestretryHandler implements URLStreamHandlerFactory.
class TestretryHandler extends URLStreamHandlerFactory {
  def createURLStreamHandler(protocol: String): URLStreamHandler =
    if (protocol == "testretry")
      new URLStreamHandler {
        protected def openConnection(url: URL): HttpURLConnection = {
          val connection = TestretryHandler.connections.incrementAndGet()
          new HttpURLConnection(url) {
            // CacheUrl.closeConn calls getInputStream() again after the download
            // to drain/close the connection. Only the first call per connection
            // instance is a real download attempt.
            private var firstCall = true

            def connect(): Unit       = ()
            def disconnect(): Unit    = ()
            def usingProxy(): Boolean = false

            override def getResponseCode: Int =
              if (connection <= TestretryHandler.rateLimitUntilConnection) 429
              else TestretryHandler.responseCode

            override def getHeaderField(name: String): String =
              TestretryHandler.responseHeaders.getOrElse(name, null)

            override def getInputStream: InputStream =
              new ByteArrayInputStream(
                // closeConn() drains HEAD connections too, and that is not a download attempt
                if (firstCall && getRequestMethod != "HEAD") {
                  firstCall = false
                  val n = TestretryHandler.attempts.incrementAndGet()
                  if (TestretryHandler.failUntilAttempt.forall(n <= _))
                    throw TestretryHandler.createException(n)
                  else
                    "fake-content".getBytes("UTF-8")
                }
                else
                  Array.emptyByteArray
              )

            override def getContentLengthLong: Long = -1L
          }
        }
      }
    else
      null
}
