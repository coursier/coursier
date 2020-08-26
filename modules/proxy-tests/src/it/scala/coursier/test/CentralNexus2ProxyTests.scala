package coursier.test

import coursier.maven.MavenRepository

object CentralNexus2ProxyTests extends CentralTests {

  val repo = DockerServer(
    "sonatype/nexus:2.14.4",
    "nexus/content/repositories/central",
    8081 -> 9081
  )

  override def utestAfterAll(): Unit =
    repo.shutdown()

  override def central =
    MavenRepository(repo.base)
      .withVersionsCheckHasModule(false)
}
