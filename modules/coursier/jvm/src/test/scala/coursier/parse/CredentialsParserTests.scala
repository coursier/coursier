package coursier.parse

import coursier.credentials.DirectCredentials
import utest._

object CredentialsParserTests extends TestSuite {

  val tests = Tests {

    test("simple") {
      val s   = "artifacts.foo.com(tha realm) alex:my-pass"
      val res = CredentialsParser.parse(s)
      val expectedRes =
        Right(DirectCredentials("artifacts.foo.com", "alex", "my-pass").withRealm("tha realm"))
      assert(res == expectedRes)
    }

    test("noRealm") {
      val s           = "artifacts.foo.com alex:my-pass"
      val res         = CredentialsParser.parse(s)
      val expectedRes = Right(DirectCredentials("artifacts.foo.com", "alex", "my-pass"))
      assert(res == expectedRes)
    }

    test("space in user name") {
      val s   = "artifacts.foo.com(tha realm) alex a:my-pass"
      val res = CredentialsParser.parse(s)
      val expectedRes =
        Right(DirectCredentials("artifacts.foo.com", "alex a", "my-pass").withRealm("tha realm"))
      assert(res == expectedRes)
    }

    test("special chars in password") {
      val s   = "artifacts.foo.com(tha realm) alex:$%_^12//,.;:"
      val res = CredentialsParser.parse(s)
      val expectedRes =
        Right(DirectCredentials("artifacts.foo.com", "alex", "$%_^12//,.;:").withRealm("tha realm"))
      assert(res == expectedRes)
    }

    test("seq") {
      test("empty") {
        val res         = CredentialsParser.parseSeq("").either
        val expectedRes = Right(Seq())
        assert(res == expectedRes)
      }

      test("one") {
        val res         = CredentialsParser.parseSeq("artifacts.foo.com alex:my-pass").either
        val expectedRes = Right(Seq(DirectCredentials("artifacts.foo.com", "alex", "my-pass")))
        assert(res == expectedRes)
      }

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
