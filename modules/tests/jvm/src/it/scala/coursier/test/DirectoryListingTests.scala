package coursier.test

import coursier.{Attributes, MavenRepository, moduleString}
import coursier.core.{Authentication, Type}
import coursier.test.compatibility.executionContext
import utest._

object DirectoryListingTests extends TestSuite {

  val user = Option(System.getenv("TEST_REPOSITORY_USER"))
    .getOrElse(sys.error("TEST_REPOSITORY_USER not set"))
  val password = Option(System.getenv("TEST_REPOSITORY_PASSWORD"))
    .getOrElse(sys.error("TEST_REPOSITORY_PASSWORD not set"))

  val repo = MavenRepository(
    Option(System.getenv("TEST_REPOSITORY")).getOrElse(sys.error("TEST_REPOSITORY not set")),
    authentication = Some(Authentication(user, password))
  )

  val module = mod"com.abc:test"
  val version = "0.1"

  private val runner = new TestRunner

  val tests = Tests {
    test("jar") - runner.withArtifacts(
      module,
      version,
      attributes = Attributes(Type.jar),
      extraRepos = Seq(repo)
    ) {
      artifacts =>
        assert(artifacts.length == 1)
        assert(artifacts.headOption.exists(_.url.endsWith(".jar")))
    }

    test("jarFoo") - runner.withArtifacts(
      module,
      version,
      attributes = Attributes(Type("jar-foo")),
      extraRepos = Seq(repo)
    ) {
      artifacts =>
        assert(artifacts.length == 1)
        assert(artifacts.headOption.exists(_.url.endsWith(".jar-foo")))
    }
  }

}
