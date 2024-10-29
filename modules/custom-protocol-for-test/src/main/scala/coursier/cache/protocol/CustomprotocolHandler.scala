package coursier.cache.protocol

import java.io.InputStream
import java.net.{URL, URLConnection, URLStreamHandler, URLStreamHandlerFactory}
import java.nio.file.{Files, Paths}

class CustomprotocolHandler extends URLStreamHandlerFactory {

  lazy val customProtocolBase = Option(System.getenv("COURSIER_CUSTOMPROTOCOL_BASE"))
    .map(Paths.get(_))
    .getOrElse {
      sys.error("COURSIER_CUSTOMPROTOCOL_BASE not set")
    }

  def createURLStreamHandler(protocol: String): URLStreamHandler = new URLStreamHandler {
    protected def openConnection(url: URL): URLConnection =
      new URLConnection(url) {
        def connect(): Unit = ()
        override def getInputStream(): InputStream =
          Files.newInputStream(customProtocolBase.resolve(url.getPath.stripPrefix("/")))
      }
  }
}
