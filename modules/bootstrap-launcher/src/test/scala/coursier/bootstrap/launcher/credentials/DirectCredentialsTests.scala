package coursier.bootstrap.launcher.credentials

import java.net.URL

import utest._

import scala.compat.java8.OptionConverters._

object DirectCredentialsTests extends TestSuite {

  val tests = Tests {
    test("no password in toString") {
      val cred = new DirectCredentials("host", "alex", "1234")
      assert(cred.getUsernameOpt.asScala.contains("alex"))
      assert(cred.getPasswordOpt.asScala.map(_.getValue).contains("1234"))
      assert(cred.toString.contains("alex"))
      assert(!cred.toString.contains("1234"))
    }

    test("URL matching") {
      def matches(cred: DirectCredentials, url: String): Boolean =
        cred.urlMatches(new URL(url))

      test("HTTPS only by default") {
        val cred = new DirectCredentials("repo.example.com", "alex", "1234")
        assert(matches(cred, "https://repo.example.com/maven/a.pom"))
        assert(matches(cred, "https://repo.example.com:8443/maven/a.pom"))
        assert(!matches(cred, "http://repo.example.com/maven/a.pom"))
        assert(!matches(cred, "https://other.example.com/maven/a.pom"))
      }

      test("HTTP allowed") {
        val cred = new DirectCredentials("repo.example.com", "alex", "1234").withHttpsOnly(false)
        assert(matches(cred, "http://repo.example.com/maven/a.pom"))
        assert(matches(cred, "http://repo.example.com:8081/maven/a.pom"))
      }

      test("host with port") {
        val cred = new DirectCredentials("repo.example.com:8443", "alex", "1234")
        assert(matches(cred, "https://repo.example.com:8443/maven/a.pom"))
        assert(!matches(cred, "https://repo.example.com/maven/a.pom"))
        assert(!matches(cred, "http://repo.example.com:8443/maven/a.pom"))
      }

      test("HTTPS origin") {
        val cred = new DirectCredentials("https://repo.example.com", "alex", "1234")
          .withHttpsOnly(false)
        assert(matches(cred, "https://repo.example.com/maven/a.pom"))
        assert(matches(cred, "https://repo.example.com:443/maven/a.pom"))
        assert(!matches(cred, "https://repo.example.com:8443/maven/a.pom"))
        assert(!matches(cred, "http://repo.example.com/maven/a.pom"))
      }

      test("HTTP origin") {
        val cred = new DirectCredentials("http://nexus.local:8081", "alex", "1234")
        assert(matches(cred, "http://nexus.local:8081/repository/a.pom"))
        assert(!matches(cred, "http://nexus.local/repository/a.pom"))
        assert(!matches(cred, "https://nexus.local:8081/repository/a.pom"))
      }
    }
  }

}
