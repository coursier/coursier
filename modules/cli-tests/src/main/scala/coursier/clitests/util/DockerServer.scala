package coursier.clitests.util

final case class DockerServer(
  base: String,
  shutdown: () => Unit,
  address: String
)
