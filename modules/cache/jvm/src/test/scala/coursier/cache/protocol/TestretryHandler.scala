package coursier.cache.protocol

import java.io.{ByteArrayInputStream, InputStream}
import java.net.{HttpURLConnection, SocketException, URL, URLStreamHandler, URLStreamHandlerFactory}
import java.util.concurrent.atomic.AtomicInteger

object TestretryHandler {
  val attempts: AtomicInteger                 = new AtomicInteger(0)
  @volatile var failUntilAttempt: Option[Int] = Some(0)

  private def defaultCreateException: Int => Throwable =
    n => new SocketException(s"Simulated SocketException on attempt $n")
  var createException: Int => Throwable = defaultCreateException
  private def defaultResponseCode       = 200
  var responseCode: Int                 = defaultResponseCode

  def reset(failUntil: Int = -1): Unit = {
    attempts.set(0)
    failUntilAttempt = Some(failUntil).filter(_ >= 0)
    createException = defaultCreateException
    responseCode = defaultResponseCode
  }
}

// Discovered by CacheUrl via classpath convention: protocol "testretry" →
// class coursier.cache.protocol.TestretryHandler implements URLStreamHandlerFactory.
class TestretryHandler extends URLStreamHandlerFactory {
  def createURLStreamHandler(protocol: String): URLStreamHandler =
    if (protocol == "testretry")
      new URLStreamHandler {
        protected def openConnection(url: URL): HttpURLConnection =
          new HttpURLConnection(url) {
            // CacheUrl.closeConn calls getInputStream() again after the download
            // to drain/close the connection. Only the first call per connection
            // instance is a real download attempt.
            private var firstCall = true

            def connect(): Unit       = ()
            def disconnect(): Unit    = ()
            def usingProxy(): Boolean = false

            override def getResponseCode: Int = TestretryHandler.responseCode

            override def getInputStream: InputStream =
              new ByteArrayInputStream(
                if (firstCall) {
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
    else
      null
}
