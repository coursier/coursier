package coursier.cache.protocol

import java.io.File
import java.io.InputStream
import java.net.{URL, URLConnection, URLStreamHandler, URLStreamHandlerFactory}
import java.io.FileInputStream

class CustomprotocolHandler extends URLStreamHandlerFactory {
  def createURLStreamHandler(protocol: String): URLStreamHandler = new URLStreamHandler {
    protected def openConnection(url: URL): URLConnection =
      new URLConnection(url) {
        def connect(): Unit = ()
        override def getInputStream(): InputStream =
          new FileInputStream(new File(new File("."), url.getPath.stripPrefix("/")))
      }
  }
}
