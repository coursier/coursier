package coursier.clitests.util

// adapted from https://github.com/VirtusLab/scala-cli/blob/c47fbf11d8e8b26a4d27eba77ab135f0b916377a/modules/integration/src/test/scala/scala/cli/integration/util/DockerServer.scala

import com.spotify.docker.client.DefaultDockerClient
import com.spotify.docker.client.messages.{ContainerConfig, HostConfig, PortBinding}

import scala.jdk.CollectionConverters._
import scala.util.Try

final case class DockerServer(
  base: String,
  shutdown: () => Unit,
  address: String
)

object DockerServer {
  def withServer[T](
    image: String,
    basePath: String,
    // can't find a way to get back a randomly assigned port (even following https://github.com/spotify/docker-client/issues/625)
    // so that one has to be specified
    portMapping: (Int, Int)
  )(f: DockerServer => T): T = {

    val (imagePort, hostPort) = portMapping
    val addr                  = s"localhost:$hostPort"

    def log(s: String): Unit =
      Console.err.println(s"[$image @ $addr] $s")

    val docker = DefaultDockerClient.fromEnv().build()
    docker.pull(image)

    val portBindings = Map(imagePort.toString -> Seq(PortBinding.of("0.0.0.0", hostPort)).asJava)

    val hostConfig = HostConfig.builder().portBindings(portBindings.asJava).build()

    val containerConfig = ContainerConfig.builder()
      .hostConfig(hostConfig)
      .image(image)
      .exposedPorts(portBindings.keys.toSeq: _*)
      .build()

    var idOpt = Option.empty[String]

    def shutdown(): Unit =
      for (id <- idOpt) {
        Try(docker.killContainer(id))
        docker.removeContainer(id)
        docker.close()
      }

    try {
      val creation = docker.createContainer(containerConfig)

      val id = creation.id()
      idOpt = Some(id)

      log(s"starting container $id")
      docker.startContainer(id)

      val base = s"http://localhost:$hostPort/$basePath"

      log(s"waiting for $image server to be up-and-running")

      Thread.sleep(2000L)

      val server = DockerServer(base, () => shutdown(), addr)
      f(server)
    }
    finally shutdown()
  }
}
