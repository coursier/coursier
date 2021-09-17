package coursier.test

import utest._

import coursier.core.Authentication
import coursier.maven.MavenRepository

object HttpAuthenticationTests extends TestSuite {

  val tests = Tests {
    test("httpAuthentication") {

      val testRepo = Option(System.getenv("TEST_REPOSITORY"))
        .orElse(sys.props.get("test.repository"))
        .getOrElse(sys.error("TEST_REPOSITORY not set"))
      val user = Option(System.getenv("TEST_REPOSITORY_USER"))
        .orElse(sys.props.get("test.repository.user"))
        .getOrElse(sys.error("TEST_REPOSITORY_USER not set"))
      val password = Option(System.getenv("TEST_REPOSITORY_PASSWORD"))
        .orElse(sys.props.get("test.repository.password"))
        .getOrElse(sys.error("TEST_REPOSITORY_PASSWORD not set"))

      test {
        // no authentication -> should fail

        val failed =
          try {
            CacheFetchTests.check(
              MavenRepository(testRepo)
            )

            false
          }
          catch {
            case _: Throwable =>
              true
          }

        assert(failed)
      }

      test {
        // with authentication -> should work

        CacheFetchTests.check(
          MavenRepository(
            testRepo,
            authentication = Some(Authentication(user, password))
          )
        )
      }
    }
  }

}
