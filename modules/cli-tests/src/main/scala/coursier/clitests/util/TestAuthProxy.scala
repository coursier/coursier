package coursier.clitests.util

import java.net.ServerSocket
import java.util.UUID

import scala.concurrent.duration._

object TestAuthProxy {

  private lazy val imageId = sys.props.getOrElse(
    "coursier.test.auth-proxy-image",
    sys.error("coursier.test.auth-proxy-image not set")
  )
  private lazy val dataPath = sys.props.getOrElse(
    "coursier.test.auth-proxy-data-dir",
    sys.error("coursier.test.auth-proxy-data-dir not set")
  )

  def withAuthProxy[T](port: Int)(f: DockerServer => T): T = {
    var containerId: String = ""
    try {
      containerId =
        os.proc(
          "docker",
          "run",
          "-d",
          "--rm",
          "-v",
          s"$dataPath:/data",
          "-p",
          s"$port:80",
          imageId,
          "/bin/sh",
          "/data/run.sh"
        )
          .call().out.trim()
      val server          = DockerServer("", () => (), s"localhost:$port")
      var serverListening = false
      var delay           = 1.second
      var count           = 0
      while (!serverListening && count < 5) {
        val lines = os.proc("docker", "logs", containerId).call().out.lines()
        serverListening = lines.contains("Starting server")
        if (!serverListening) {
          System.err.println(s"Waiting for auth proxy to listen on ${server.address}")
          Thread.sleep(delay.toMillis)
          delay = 2 * delay
          count += 1
        }
      }
      Thread.sleep(5000L) // give the server a few seconds to start just in case
      f(server)
    }
    finally
      if (containerId.nonEmpty)
        os.proc("docker", "rm", "-f", containerId).call(stdin = os.Inherit, stdout = os.Inherit)
  }

  def withAuthProxy0[T](f: (String, String, Int) => T): T = {
    val networkName = "cs-test-" + UUID.randomUUID().toString
    var containerId = ""
    try {
      os.proc("docker", "network", "create", networkName)
        .call(stdin = os.Inherit, stdout = os.Inherit)
      val host = {
        val res  = os.proc("docker", "network", "inspect", networkName).call(stdin = os.Inherit)
        val resp = ujson.read(res.out.trim())
        resp.arr(0).apply("IPAM").apply("Config").arr(0).apply("Gateway").str
      }
      val port = {
        val s = new ServerSocket(0)
        try s.getLocalPort
        finally s.close()
      }
      val res = os.proc(
        "docker",
        "run",
        "-d",
        "--rm",
        "-v",
        s"$dataPath:/data",
        "-p",
        s"$port:80",
        "--network",
        networkName,
        imageId,
        "/bin/sh",
        "/data/run.sh"
      )
        .call(stdin = os.Inherit)
      containerId = res.out.trim()
      f(networkName, host, port)
    }
    finally {
      if (containerId.nonEmpty) {
        System.err.println(s"Removing container $containerId")
        os.proc("docker", "rm", "-f", containerId)
          .call(stdin = os.Inherit, stdout = os.Inherit)
      }
      os.proc("docker", "network", "rm", networkName)
        .call(stdin = os.Inherit, stdout = os.Inherit)
    }
  }

  def m2Settings(port: Int, user: String, password: String): String =
    s"""<settings>
       |<proxies>
       |   <proxy>
       |      <id>test-proxy</id>
       |      <protocol>http</protocol>
       |      <host>localhost</host>
       |      <port>$port</port>
       |      <username>$user</username>
       |      <password>$password</password>
       |    </proxy>
       |  </proxies>
       |</settings>
       |""".stripMargin

  def m2Settings(port: Int): String =
    m2Settings(port, "jack", "insecure")

}
