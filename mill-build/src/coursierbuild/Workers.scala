package coursierbuild

import coursierbuild.Deps.ScalaVersions

import mill._, scalalib._

import java.io.{InputStream, IOException}

object Workers {
  final class TestRepoServer(
    val host: String = "localhost",
    val port: Int = randomPort(),
    val user: String = "user",
    val password: String = "pass",
    var proc: os.SubProcess = null
  ) extends AutoCloseable {
    def url = s"http://$host:$port"

    def healthCheck(): Boolean = {
      val url0            = new java.net.URL(s"$url/com/abc/test/0.1/test-0.1.pom.sha1")
      var is: InputStream = null
      try {
        println(s"Checking $url0")
        is = url0.openStream()
        scala.io.Source.fromInputStream(is).mkString
        true
      }
      catch {
        case ex: IOException =>
          ex.getMessage.contains("401")
      }
      finally if (is != null)
          is.close()
    }

    override def close(): Unit =
      proc.destroy(shutdownGracePeriod = 0)
  }

  private def randomPort(): Int = {
    val s    = new java.net.ServerSocket(0)
    val port = s.getLocalPort
    s.close()
    port
  }
}
