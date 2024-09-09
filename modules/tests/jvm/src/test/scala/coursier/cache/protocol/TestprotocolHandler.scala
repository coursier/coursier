package coursier.cache.protocol

import java.io.File
import java.net.{URL, URLConnection, URLStreamHandler, URLStreamHandlerFactory}

import coursier.tests.HandmadeMetadata

class TestprotocolHandler extends URLStreamHandlerFactory {

  def createURLStreamHandler(protocol: String): URLStreamHandler = new URLStreamHandler {
    protected def openConnection(url: URL): URLConnection = {
      val f         = new File(HandmadeMetadata.repoBase, "http/abc.com" + url.getPath)
      val resURLOpt = Option(f.toURI.toURL)

      resURLOpt match {
        case None =>
          new URLConnection(url) {
            def connect() = throw new NoSuchElementException(f.getAbsolutePath)
          }
        case Some(resURL) =>
          resURL.openConnection()
      }
    }
  }
}

object TestprotocolHandler {
  val protocol = "testprotocol"

  // get this namespace via a macro?
  val expectedClassName = s"coursier.cache.protocol.${protocol.capitalize}Handler"
  assert(classOf[TestprotocolHandler].getName == expectedClassName)
}
