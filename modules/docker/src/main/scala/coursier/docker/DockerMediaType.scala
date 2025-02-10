package coursier.docker

object DockerMediaType {
  def json(name: String): String =
    s"application/vnd.oci.$name.v1+json"
  def tarGz(name: String): String =
    s"application/vnd.oci.$name.v1.tar+gzip"
}
