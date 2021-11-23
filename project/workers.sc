import $file.deps, deps.ScalaVersions

import mill._, scalalib._

import java.io.{File, InputStream, IOException}

final case class RedirectingServer(
  host: String = "localhost",
  port: Int = randomPort(),
  var proc: os.SubProcess = null
) extends java.io.Closeable {
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
    proc.destroyForcibly()
}

trait UsesRedirectingServer extends Module {
  def redirectingServerCp: T[Seq[PathRef]]
  def redirectingServerMainClass: T[String]

  def redirectingServer = T.worker {
    val cp        = redirectingServerCp().map(_.path)
    val mainClass = redirectingServerMainClass()

    val server = RedirectingServer()

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
    while (!serverRunning && server.proc.isAlive && countDown > 0) {
      serverRunning = server.healthCheck()
      if (!serverRunning)
        Thread.sleep(500L)
      countDown -= 1
    }
    if (serverRunning && server.proc.isAlive)
      server
    else
      sys.error("Cannot run redirecting server")
  }
}

final case class TestRepoServer(
  host: String = "localhost",
  port: Int = randomPort(),
  user: String = "user",
  password: String = "pass",
  var proc: os.SubProcess = null
) extends java.io.Closeable {
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
    proc.destroyForcibly()
}

def testRepoServer = T.worker {
  val server = TestRepoServer()

  if (server.healthCheck())
    sys.error("Test repo server already running")

  server.proc = os.proc(
    "cs",
    "launch",
    "io.get-coursier:http-server_2.12:1.0.0",
    "--",
    "-d",
    "modules/tests/handmade-metadata/data/http/abc.com",
    "-u",
    server.user,
    "-P",
    server.password,
    "-r",
    "realm",
    "-v",
    "--host",
    server.host,
    "--port",
    server.port.toString
  ).spawn(
    stdin = os.Pipe,
    stdout = os.Inherit,
    stderr = os.Inherit
  )
  server.proc.stdin.close()
  var serverRunning = false
  var countDown     = 20
  while (!serverRunning && server.proc.isAlive && countDown > 0) {
    serverRunning = server.healthCheck()
    if (!serverRunning)
      Thread.sleep(500L)
    countDown -= 1
  }
  if (serverRunning && server.proc.isAlive) {
    T.log.outputStream.println(s"Test repository listening on ${server.url}")
    server
  }
  else
    sys.error("Cannot run test repo server")
}

private def randomPort(): Int = {
  val s    = new java.net.ServerSocket(0)
  val port = s.getLocalPort
  s.close()
  port
}
