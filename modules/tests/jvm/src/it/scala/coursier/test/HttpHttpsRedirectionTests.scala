package coursier.test

import coursier.{Dependency, moduleString}
import coursier.maven.MavenRepository
import utest._

object HttpHttpsRedirectionTests extends TestSuite {

  val tests = Tests {

    val testRepo = sys.env.getOrElse("TEST_REDIRECT_REPOSITORY", sys.error("TEST_REDIRECT_REPOSITORY not set"))
    val deps = Set(Dependency(mod"com.chuusai:shapeless_2.12", "2.3.2"))

    * - {
      // no redirections -> should fail

      val failed = try {
        CacheFetchTests.check(
          MavenRepository(testRepo),
          addCentral = false,
          deps = deps
        )

        false
      } catch {
        case _: Throwable =>
          true
      }

      assert(failed)
    }

    * - {
      // with redirection -> should work

      CacheFetchTests.check(
        MavenRepository(testRepo),
        addCentral = false,
        deps = deps,
        followHttpToHttpsRedirections = true
      )
    }
  }

}
