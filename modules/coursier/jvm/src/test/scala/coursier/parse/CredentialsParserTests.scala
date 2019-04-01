package coursier.parse

import coursier.cache.Credentials
import utest._

object CredentialsParserTests extends TestSuite {

  val tests = Tests {

    'simple - {
      val s = "artifacts.foo.com(tha realm) alex:my-pass"
      val res = CredentialsParser.parse(s)
      val expectedRes = Right(Credentials("artifacts.foo.com", "alex", "my-pass").withRealm("tha realm"))
      assert(res == expectedRes)
    }

    'noRealm - {
      val s = "artifacts.foo.com alex:my-pass"
      val res = CredentialsParser.parse(s)
      val expectedRes = Right(Credentials("artifacts.foo.com", "alex", "my-pass"))
      assert(res == expectedRes)
    }

    "space in user name" - {
      val s = "artifacts.foo.com(tha realm) alex a:my-pass"
      val res = CredentialsParser.parse(s)
      val expectedRes = Right(Credentials("artifacts.foo.com", "alex a", "my-pass").withRealm("tha realm"))
      assert(res == expectedRes)
    }

    "special chars in password" - {
      val s = "artifacts.foo.com(tha realm) alex:$%_^12//,.;:"
      val res = CredentialsParser.parse(s)
      val expectedRes = Right(Credentials("artifacts.foo.com", "alex", "$%_^12//,.;:").withRealm("tha realm"))
      assert(res == expectedRes)
    }

    "seq" - {
      'empty - {
        val res = CredentialsParser.parseSeq("")
        val expectedRes = Right(Seq())
        assert(res == expectedRes)
      }

      "one" - {
        val res = CredentialsParser.parseSeq("artifacts.foo.com alex:my-pass")
        val expectedRes = Right(Seq(Credentials("artifacts.foo.com", "alex", "my-pass")))
        assert(res == expectedRes)
      }

      "several" - {
        val res = CredentialsParser.parseSeq(
          """artifacts.foo.com alex:my-pass
            |
            |  artifacts.foo.com(tha realm) alex a:my-pass
            |artifacts.foo.com(tha realm) alex:$%_^12//,.;:   """.stripMargin
        )
        val expectedRes = Right(Seq(
          Credentials("artifacts.foo.com", "alex", "my-pass"),
          Credentials("artifacts.foo.com", "alex a", "my-pass").withRealm("tha realm"),
          Credentials("artifacts.foo.com", "alex", "$%_^12//,.;:   ").withRealm("tha realm")
        ))
        assert(res == expectedRes)
      }
    }

  }

}
