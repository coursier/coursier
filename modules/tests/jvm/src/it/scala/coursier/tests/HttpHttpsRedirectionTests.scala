package coursier.tests

import java.io.{File, IOException, InputStream}
import java.net.ServerSocket
import java.nio.charset.StandardCharsets

import coursier.core.Dependency
import coursier.maven.MavenRepository
import coursier.util.StringInterpolators._
import coursier.version.VersionConstraint
import utest._

import scala.util.Properties

object HttpHttpsRedirectionTests extends TestSuite {

  private final class RedirectingServer(
    val host: String = "localhost",
    val port: Int = randomPort(),
    proc0: Process = null
  ) extends AutoCloseable {
    private var proc = proc0

    def url: String = s"http://$host:$port"

    def healthCheck(): Boolean = {
      val url0            = new java.net.URL(s"$url/health-check")
      var is: InputStream = null
      try {
        is = url0.openStream()
        new String(is.readAllBytes(), StandardCharsets.UTF_8)
        true
      }
      catch {
        case _: IOException =>
          false
      }
      finally if (is != null)
          is.close()
    }

    def isAlive(): Boolean =
      proc != null && proc.isAlive()

    def start(): RedirectingServer = {
      if (healthCheck())
        sys.error("Server already running")

      val javaHome = new File(sys.props("java.home"))
      val javaBin = new File(
        new File(javaHome, "bin"),
        if (Properties.isWin) "java.exe" else "java"
      )

      val builder = new ProcessBuilder(
        javaBin.getAbsolutePath,
        "-Xmx128m",
        "-cp",
        sys.props.getOrElse(
          "test.redirecting-server.classpath",
          sys.error("test.redirecting-server.classpath not set")
        ),
        "redirectingserver.RedirectingServer",
        host,
        port.toString
      )
      builder.redirectInput(ProcessBuilder.Redirect.PIPE)
      builder.redirectOutput(ProcessBuilder.Redirect.INHERIT)
      builder.redirectError(ProcessBuilder.Redirect.INHERIT)

      proc = builder.start()
      proc.getOutputStream.close()

      var serverRunning = false
      var countDown     = 30
      while (!serverRunning && isAlive() && countDown > 0) {
        serverRunning = healthCheck()
        if (!serverRunning)
          Thread.sleep(500L)
        countDown -= 1
      }

      if (serverRunning && isAlive())
        this
      else {
        close()
        sys.error("Cannot run redirecting server")
      }
    }

    override def close(): Unit =
      if (proc != null)
        proc.destroy()
  }

  private lazy val redirectingServer = new RedirectingServer().start()

  override def utestAfterAll(): Unit =
    redirectingServer.close()

  val tests = Tests {

    lazy val testRepo = redirectingServer.url
    val deps          = Seq(Dependency(mod"com.chuusai:shapeless_2.12", VersionConstraint("2.3.2")))

    test {
      // no redirections -> should fail

      val failed =
        try {
          CacheFetchTests.check(
            MavenRepository(testRepo),
            addCentral = false,
            deps = deps
          )

          false
        }
        catch {
          case _: Throwable =>
            true
        }

      assert(failed)
    }

    test {
      // with redirection -> should work

      CacheFetchTests.check(
        MavenRepository(testRepo),
        addCentral = false,
        deps = deps,
        followHttpToHttpsRedirections = true
      )
    }
  }

  private def randomPort(): Int = {
    val s    = new ServerSocket(0)
    val port = s.getLocalPort
    s.close()
    port
  }
}
