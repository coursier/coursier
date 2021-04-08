package coursier.test

import coursier.maven.MavenRepository
import utest._

import scala.concurrent.duration.DurationInt
import scala.util.Properties.isWin

object CentralNexus3ProxyTests extends CentralTests {

  lazy val repo = DockerServer(
    "sonatype/nexus3:3.3.1",
    "repository/maven-central/", // 400 error without the trailing '/'
    8081 -> 9082,
    timeout = 3.minutes // !!!
  )

  if (!isWin)
    repo // eagerly create repo

  override def utestAfterAll(): Unit =
    if (!isWin)
      repo.shutdown()

  override def central =
    MavenRepository(repo.base.stripSuffix("/"))
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
