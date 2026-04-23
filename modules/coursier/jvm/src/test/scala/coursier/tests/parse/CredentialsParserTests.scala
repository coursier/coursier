package coursier.tests.parse

import coursier.parse._
import coursier.credentials.DirectCredentials
import utest._

object CredentialsParserTests extends TestSuite {

  val tests = Tests {

    /** Verifies the `simple` scenario behaves as the user expects. */
    test("simple") {
      val s   = "artifacts.foo.com(tha realm) alex:my-pass"
      val res = CredentialsParser.parse(s)
      val expectedRes =
        Right(DirectCredentials("artifacts.foo.com", "alex", "my-pass").withRealm("tha realm"))
      assert(res == expectedRes)
    }

    /** Verifies the `noRealm` scenario behaves as the user expects. */
    test("noRealm") {
      val s           = "artifacts.foo.com alex:my-pass"
      val res         = CredentialsParser.parse(s)
      val expectedRes = Right(DirectCredentials("artifacts.foo.com", "alex", "my-pass"))
      assert(res == expectedRes)
    }

    /** Verifies the `space in user name` scenario behaves as the user expects. */
    test("space in user name") {
      val s   = "artifacts.foo.com(tha realm) alex a:my-pass"
      val res = CredentialsParser.parse(s)
      val expectedRes =
        Right(DirectCredentials("artifacts.foo.com", "alex a", "my-pass").withRealm("tha realm"))
      assert(res == expectedRes)
    }

    /** Verifies the `special chars in password` scenario behaves as the user expects. */
    test("special chars in password") {
      val s   = "artifacts.foo.com(tha realm) alex:$%_^12//,.;:"
      val res = CredentialsParser.parse(s)
      val expectedRes =
        Right(DirectCredentials("artifacts.foo.com", "alex", "$%_^12//,.;:").withRealm("tha realm"))
      assert(res == expectedRes)
    }

    /** Verifies the `seq` scenario behaves as the user expects. */
    test("seq") {
      /** Verifies the `empty` scenario behaves as the user expects. */
      test("empty") {
        val res         = CredentialsParser.parseSeq("").either
        val expectedRes = Right(Seq())
        assert(res == expectedRes)
      }

      /** Verifies the `one` scenario behaves as the user expects. */
      test("one") {
        val res         = CredentialsParser.parseSeq("artifacts.foo.com alex:my-pass").either
        val expectedRes = Right(Seq(DirectCredentials("artifacts.foo.com", "alex", "my-pass")))
        assert(res == expectedRes)
      }

      /** Verifies the `several` scenario behaves as the user expects. */
      test("several") {
        val res = CredentialsParser.parseSeq(
          """artifacts.foo.com alex:my-pass
            |
            |  artifacts.foo.com(tha realm) alex a:my-pass
            |artifacts.foo.com(tha realm) alex:$%_^12//,.;:   """.stripMargin
        ).either
        val expectedRes = Right {
          Seq(
            DirectCredentials("artifacts.foo.com", "alex", "my-pass"),
            DirectCredentials("artifacts.foo.com", "alex a", "my-pass").withRealm("tha realm"),
            DirectCredentials("artifacts.foo.com", "alex", "$%_^12//,.;:   ").withRealm("tha realm")
          )
        }
        assert(res == expectedRes)
      }
    }

  }

}
