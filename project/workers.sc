import $file.deps, deps.ScalaVersions

import mill._, scalalib._

import java.io.{File, InputStream, IOException}

final class RedirectingServer(
  val host: String = "localhost",
  val port: Int = randomPort(),
  var proc: os.SubProcess = null
) extends AutoCloseable {
  def url = s"http://$host:$port"

  def healthCheck(): Boolean = {
    val url0            = new java.net.URL(s"$url/health-check")
    var is: InputStream = null
    try {
      println(s"Checking $url0")
      is = url0.openStream()
      scala.io.Source.fromInputStream(is).mkString
      true
    }
    catch {
      case _: IOException =>
        false
    }
    finally if (is != null)
        is.close()
  }

  override def close(): Unit =
    proc.destroy(shutdownGracePeriod = 0)
}

trait UsesRedirectingServer extends Module {
  def redirectingServerCp: T[Seq[PathRef]]
  def redirectingServerMainClass: T[String]

  def redirectingServer = T.worker {
    val cp        = redirectingServerCp().map(_.path)
    val mainClass = redirectingServerMainClass()

    val server = new RedirectingServer

    if (server.healthCheck())
      sys.error("Server already running")

    server.proc = os.proc(
      "java",
      "-Xmx128m",
      "-cp",
      cp.mkString(File.pathSeparator),
      mainClass,
      server.host,
      server.port.toString
    ).spawn(
      stdin = os.Pipe,
      stdout = os.Inherit,
      stderr = os.Inherit
    )
    server.proc.stdin.close()
    var serverRunning = false
    var countDown     = 20
    while (!serverRunning && server.proc.isAlive() && countDown > 0) {
      serverRunning = server.healthCheck()
      if (!serverRunning)
        Thread.sleep(500L)
      countDown -= 1
    }
    if (serverRunning && server.proc.isAlive())
      server
    else
      sys.error("Cannot run redirecting server")
  }
}

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
