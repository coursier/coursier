package coursier.tests

import utest._

import coursier.core.Authentication
import coursier.maven.MavenRepository
import coursier.testcache.TestRepositoryServer

object HttpAuthenticationTests extends TestSuite with TestRepositoryServer.Test {

  private def testRepo = localTestRepo().url

  val tests = Tests {
    test("httpAuthentication") {
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
            authentication = Some(Authentication(localTestRepo().user, localTestRepo().password))
          )
        )
      }
    }
  }

}
