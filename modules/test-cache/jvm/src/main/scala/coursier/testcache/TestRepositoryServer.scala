package coursier.testcache

import java.io.{IOException, InputStream}
import java.net.{ServerSocket, URL}
import java.nio.file.Paths
import java.util.concurrent.{TimeUnit, TimeoutException}

import scala.collection.mutable.ListBuffer
import scala.jdk.CollectionConverters._
import scala.util.{Properties, Using}

abstract class TestRepositoryServer extends AutoCloseable {
  def url: String
  def user: String
  def password: String
}

object TestRepositoryServer {

  private def processTree(process: Process): Seq[ProcessHandle] = {
    val handles = ListBuffer.empty[ProcessHandle]
    val root    = process.toHandle
    handles += root

    Using.resource(root.descendants()) { descendants =>
      handles ++= descendants.iterator().asScala
    }

    handles.toVector
  }

  private def waitForExit(handle: ProcessHandle, timeoutMillis: Long): Boolean =
    !handle.isAlive() || {
      try {
        handle.onExit().get(timeoutMillis, TimeUnit.MILLISECONDS)
        true
      }
      catch {
        case _: TimeoutException =>
          false
        case _: InterruptedException =>
          Thread.currentThread().interrupt()
          false
      }
    }

  private[coursier] def destroyProcessTree(process: Process, label: String): Unit = {
    val handles = processTree(process)
    System.err.println(s"Stopping $label (${handles.length} process(es))")

    handles.foreach(_.destroy())
    val stillAlive = handles.filter(h => h.isAlive() && !waitForExit(h, 5000L))

    if (stillAlive.isEmpty)
      System.err.println(s"Stopped $label")
    else {
      System.err.println(s"Forcibly stopping $label (${stillAlive.length} process(es))")
      stillAlive.foreach(_.destroyForcibly())

      val remaining = stillAlive.filter(h => h.isAlive() && !waitForExit(h, 5000L))
      System.err.println(
        if (remaining.isEmpty)
          s"Stopped $label after forcing termination"
        else
          s"$label still has ${remaining.length} live process(es) after shutdown"
      )
    }
  }

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

      val javaBin = Paths.get(
        sys.props("java.home"),
        "bin",
        if (Properties.isWin) "java.exe" else "java"
      ).toString
      val httpServerCp = sys.props.getOrElse(
        "test.http-server.classpath",
        sys.error("test.http-server.classpath not set")
      )

      val builder = new ProcessBuilder(
        javaBin,
        "-cp",
        httpServerCp,
        "coursier.HttpServerApp",
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
      if (proc != null) {
        TestRepositoryServer.destroyProcessTree(proc, s"test repository server at $url")
        proc = null
      }
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

    override def utestAfterAll(): Unit =
      try
        localTestRepoOpt.foreach { repo =>
          System.err.println("Shutting down local test repository server")
          repo.close()
        }
      finally {
        localTestRepoOpt = None
        super.utestAfterAll()
      }
  }
}
