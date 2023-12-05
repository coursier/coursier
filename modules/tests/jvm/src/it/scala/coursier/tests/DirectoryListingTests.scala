package coursier.tests

import coursier.core.{Attributes, Authentication, Classifier, Type}
import coursier.maven.MavenRepository
import coursier.tests.compatibility.executionContext
import coursier.util.StringInterpolators._
import utest._

object DirectoryListingTests extends TestSuite {

  val user = Option(System.getenv("TEST_REPOSITORY_USER"))
    .orElse(sys.props.get("test.repository.user"))
    .getOrElse(sys.error("TEST_REPOSITORY_USER not set"))
  val password = Option(System.getenv("TEST_REPOSITORY_PASSWORD"))
    .orElse(sys.props.get("test.repository.password"))
    .getOrElse(sys.error("TEST_REPOSITORY_PASSWORD not set"))
  val repoUrl = Option(System.getenv("TEST_REPOSITORY"))
    .orElse(sys.props.get("test.repository"))
    .getOrElse(sys.error("TEST_REPOSITORY not set"))

  val repo = MavenRepository(
    repoUrl,
    authentication = Some(Authentication(user, password))
  )

  val module  = mod"com.abc:test"
  val version = "0.1"

  private val runner = new TestRunner

  val tests = Tests {
    test("jar") - runner.withArtifacts(
      module,
      version,
      attributes = Attributes(Type.jar, Classifier.empty),
      extraRepos = Seq(repo)
    ) {
      artifacts =>
        assert(artifacts.length == 1)
        assert(artifacts.headOption.exists(_.url.endsWith(".jar")))
    }

    test("jarFoo") - runner.withArtifacts(
      module,
      version,
      attributes = Attributes(Type("jar-foo"), Classifier.empty),
      extraRepos = Seq(repo)
    ) {
      artifacts =>
        assert(artifacts.length == 1)
        assert(artifacts.headOption.exists(_.url.endsWith(".jar-foo")))
    }
  }

}
