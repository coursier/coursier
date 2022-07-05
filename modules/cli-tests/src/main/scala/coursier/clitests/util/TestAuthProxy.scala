package coursier.clitests.util

object TestAuthProxy {

  def authProxyTestImage =
    "bahamat/authenticated-proxy@sha256:568c759ac687f93d606866fbb397f39fe1350187b95e648376b971e9d7596e75"

  def defaultPort = 9083

  def withAuthProxy[T](f: DockerServer => T): T =
    DockerServer.withServer(authProxyTestImage, "", 80 -> defaultPort) { server =>
      f(server)
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

  def m2Settings(): String =
    m2Settings(defaultPort, "jack", "insecure")

}
