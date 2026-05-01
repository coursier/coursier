package coursier.testcache

import java.io.{IOException, InputStream}
import java.net.{ServerSocket, URL}
import java.nio.file.Paths

import scala.util.Properties

abstract class TestRepositoryServer extends AutoCloseable {
  def url: String
  def user: String
  def password: String
}

object TestRepositoryServer {

  private final class RunningServer(
    val host: String = "localhost",
    val port: Int = randomPort(),
    val user: String = "user",
    val password: String = "pass",
    proc0: Process = null
  ) extends TestRepositoryServer {
    private var proc = proc0

    def url: String = s"http://$host:$port"

    def healthCheck(): Boolean = {
      val url0            = new URL(s"$url/com/abc/test/0.1/test-0.1.pom.sha1")
      var is: InputStream = null
      try {
        is = url0.openStream()
        is.readAllBytes()
        true
      }
      catch {
        case ex: IOException =>
          ex.getMessage.contains("401")
      }
      finally if (is != null)
          is.close()
    }

    def isAlive(): Boolean =
      proc != null && proc.isAlive()

    def start(): this.type = {
      if (healthCheck())
        sys.error("Test repo server already running")

      val dataDir = Paths.get(
        sys.props.getOrElse(
          "test.repository.data-dir",
          sys.error("test.repository.data-dir not set")
        )
      )

      val builder = new ProcessBuilder(
        "cs",
        "launch",
        "io.get-coursier:http-server_2.12:1.0.0",
        "--",
        "-d",
        dataDir.toString,
        "-u",
        user,
        "-P",
        password,
        "-r",
        "realm",
        "-v",
        "--host",
        host,
        "--port",
        port.toString
      )
      builder.redirectInput(ProcessBuilder.Redirect.PIPE)
      builder.redirectOutput(ProcessBuilder.Redirect.INHERIT)
      builder.redirectError(ProcessBuilder.Redirect.INHERIT)

      proc = builder.start()
      proc.getOutputStream.close()

      var serverRunning = false
      var countDown     = if (Properties.isWin) 120 else 20
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
        sys.error("Cannot run test repo server")
      }
    }

    override def close(): Unit =
      if (proc != null)
        proc.destroy()
  }

  lazy val repository: TestRepositoryServer =
    createRepository()

  def createRepository(): TestRepositoryServer =
    new RunningServer().start()

  private def randomPort(): Int = {
    val s    = new ServerSocket(0)
    val port = s.getLocalPort
    s.close()
    port
  }

  trait Test extends utest.framework.Executor {
    private var localTestRepoOpt  = Option.empty[TestRepositoryServer]
    private val localTestRepoLock = new Object

    def localTestRepo(): TestRepositoryServer =
      localTestRepoOpt.getOrElse {
        localTestRepoLock.synchronized {
          localTestRepoOpt.getOrElse {
            val repo = TestRepositoryServer.createRepository()
            localTestRepoOpt = Some(repo)
            repo
          }
        }
      }

    override def utestAfterAll(): Unit = {
      localTestRepoOpt.foreach(_.close())
      super.utestAfterAll()
    }
  }
}
