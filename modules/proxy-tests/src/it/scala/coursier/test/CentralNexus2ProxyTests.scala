package coursier.test

import coursier.maven.MavenRepository
import utest._

import scala.util.Properties.isWin

object CentralNexus2ProxyTests extends CentralTests {

  lazy val repo = DockerServer(
    "sonatype/nexus:2.14.4",
    "nexus/content/repositories/central",
    8081 -> 9081
  )

  if (!isWin)
    repo // eagerly create repo

  override def utestAfterAll(): Unit =
    if (!isWin)
      repo.shutdown()

  override def central =
    MavenRepository(repo.base)
      .withVersionsCheckHasModule(false)

  override def tests: Tests =
    if (isWin)
      Tests {
        test("disabled") {
          "disabled"
        }
      }
    else
      super.tests
}
