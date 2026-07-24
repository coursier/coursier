package coursier.tests

import coursier.core.{Attributes, Authentication, Classifier, Type}
import coursier.maven.MavenRepository
import coursier.testcache.TestRepositoryServer
import coursier.tests.compatibility.executionContext
import coursier.util.StringInterpolators._
import utest._

object DirectoryListingTests extends TestSuite with TestRepositoryServer.Test {

  private def user     = localTestRepo().user
  private def password = localTestRepo().password
  private def repoUrl  = localTestRepo().url

  private val repo = MavenRepository(
    repoUrl,
    authentication = Some(Authentication.create(user, password))
  )

  private val module  = mod"com.abc:test"
  private val version = "0.1"

  private val runner = new TestRunner

  val tests = Tests {
    test("jar") {
      runner.withArtifacts(
        module,
        version,
        attributes = Attributes(Type.jar, Classifier.empty),
        extraRepos = Seq(repo)
      ) {
        artifacts =>
          assert(artifacts.length == 1)
          assert(artifacts.headOption.exists(_.url.endsWith(".jar")))
      }
    }

    test("jarFoo") {
      runner.withArtifacts(
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

}
