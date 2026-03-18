package coursier.tests

import coursier.core.Dependency
import coursier.maven.MavenRepository
import coursier.util.StringInterpolators._
import coursier.version.VersionConstraint
import utest._

object HttpHttpsRedirectionTests extends TestSuite {

  val tests = Tests {

    val testRepo = Option(System.getenv("TEST_REDIRECT_REPOSITORY"))
      .orElse(sys.props.get("test.redirect.repository"))
      .getOrElse(sys.error("TEST_REDIRECT_REPOSITORY not set"))
    val deps = Seq(Dependency(mod"com.chuusai:shapeless_2.12", VersionConstraint("2.3.2")))

    // typer error in 2.11.12 if we make that a lazy val
    def enabled: Boolean = {
      val enabled0 = testRepo != "disabled"
      if (!enabled0)
        System.err.println("HttpHttpsRedirectionTests disabled (TEST_REDIRECT_REPOSITORY=disabled)")
      enabled0
    }

    test {
      // no redirections -> should fail

      if (enabled) {
        val failed =
          try {
            CacheFetchTests.check(
              MavenRepository(testRepo),
              addCentral = false,
              deps = deps
            )

            false
          }
          catch {
            case _: Throwable =>
              true
          }

        assert(failed)
      }
    }

    test {
      // with redirection -> should work

      if (enabled)
        CacheFetchTests.check(
          MavenRepository(testRepo),
          addCentral = false,
          deps = deps,
          followHttpToHttpsRedirections = true
        )
    }
  }

}
