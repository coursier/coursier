package coursier.bootstrap.launcher.credentials

import utest._

import scala.jdk.CollectionConverters._
import scala.compat.java8.OptionConverters._

object CredentialsParserTests extends TestSuite {

  val tests = Tests {

    test("simple") {
      val s = "artifacts.foo.com(tha realm) alex:my-pass"
      val res = CredentialsParser.parse(s).asScala
      val expectedRes = Some(new DirectCredentials("artifacts.foo.com", "alex", "my-pass").withRealm("tha realm"))
      assert(res == expectedRes)
    }

    test("noRealm") {
      val s = "artifacts.foo.com alex:my-pass"
      val res = CredentialsParser.parse(s).asScala
      val expectedRes = Some(new DirectCredentials("artifacts.foo.com", "alex", "my-pass"))
      assert(res == expectedRes)
    }

    test("space in user name") {
      val s = "artifacts.foo.com(tha realm) alex a:my-pass"
      val res = CredentialsParser.parse(s).asScala
      val expectedRes = Some(new DirectCredentials("artifacts.foo.com", "alex a", "my-pass").withRealm("tha realm"))
      assert(res == expectedRes)
    }

    test("special chars in password") {
      val s = "artifacts.foo.com(tha realm) alex:$%_^12//,.;:"
      val res = CredentialsParser.parse(s).asScala
      val expectedRes = Some(new DirectCredentials("artifacts.foo.com", "alex", "$%_^12//,.;:").withRealm("tha realm"))
      assert(res == expectedRes)
    }

    test("seq") {
      test("empty") {
        val res = CredentialsParser.parseList("").asScala
        val expectedRes = Seq()
        assert(res == expectedRes)
      }

      test("one") {
        val res = CredentialsParser.parseList("artifacts.foo.com alex:my-pass").asScala
        val expectedRes = Seq(new DirectCredentials("artifacts.foo.com", "alex", "my-pass"))
        assert(res == expectedRes)
      }

      test("several") {
        val res = CredentialsParser.parseList(
          """artifacts.foo.com alex:my-pass
            |
            |  artifacts.foo.com(tha realm) alex a:my-pass
            |artifacts.foo.com(tha realm) alex:$%_^12//,.;:   """.stripMargin
        ).asScala
        val expectedRes = Seq(
          new DirectCredentials("artifacts.foo.com", "alex", "my-pass"),
          new DirectCredentials("artifacts.foo.com", "alex a", "my-pass").withRealm("tha realm"),
          new DirectCredentials("artifacts.foo.com", "alex", "$%_^12//,.;:   ").withRealm("tha realm")
        )
        assert(res == expectedRes)
      }
    }

  }

}
