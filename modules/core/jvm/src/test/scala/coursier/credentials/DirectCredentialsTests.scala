package coursier.credentials

import utest._

object DirectCredentialsTests extends TestSuite {

  val tests = Tests {
    test("no password in toString") {
      val cred = DirectCredentials("host", "alex", "1234")
      assert(cred.usernameOpt.contains("alex"))
      assert(cred.passwordOpt.map(_.value).contains("1234"))
      assert(cred.toString.contains("alex"))
      assert(!cred.toString.contains("1234"))
    }

    test("HTTPS only by default") {
      val cred = DirectCredentials("repo.example.com", "alex", "1234")
      assert(cred.autoMatches("https://repo.example.com/maven/a.pom", None))
      assert(cred.autoMatches("https://repo.example.com:8443/maven/a.pom", None))
      assert(!cred.autoMatches("http://repo.example.com/maven/a.pom", None))
      assert(!cred.autoMatches("https://other.example.com/maven/a.pom", None))
      assert(cred.matches("https://repo.example.com/maven/a.pom", "alex"))
      assert(!cred.matches("http://repo.example.com/maven/a.pom", "alex"))
    }

    test("HTTP allowed") {
      val cred = DirectCredentials("repo.example.com", "alex", "1234").copy(httpsOnly = false)
      assert(cred.autoMatches("https://repo.example.com/maven/a.pom", None))
      assert(cred.autoMatches("http://repo.example.com/maven/a.pom", None))
      assert(cred.autoMatches("http://repo.example.com:8081/maven/a.pom", None))
    }

    test("host with port") {
      val cred = DirectCredentials("repo.example.com:8443", "alex", "1234")
      assert(cred.autoMatches("https://repo.example.com:8443/maven/a.pom", None))
      assert(!cred.autoMatches("https://repo.example.com/maven/a.pom", None))
      assert(!cred.autoMatches("https://repo.example.com:9443/maven/a.pom", None))
      assert(!cred.autoMatches("http://repo.example.com:8443/maven/a.pom", None))
    }

    test("HTTPS origin") {
      val cred = DirectCredentials("https://repo.example.com", "alex", "1234")
        .copy(httpsOnly = false) // ignored, as the scheme is explicit
      assert(cred.autoMatches("https://repo.example.com/maven/a.pom", None))
      assert(cred.autoMatches("https://repo.example.com:443/maven/a.pom", None))
      assert(!cred.autoMatches("https://repo.example.com:8443/maven/a.pom", None))
      assert(!cred.autoMatches("http://repo.example.com/maven/a.pom", None))
    }

    test("HTTP origin") {
      val cred = DirectCredentials("http://nexus.local:8081", "alex", "1234")
      assert(cred.httpsOnly)
      assert(cred.autoMatches("http://nexus.local:8081/repository/a.pom", None))
      assert(cred.matches("http://nexus.local:8081/repository/a.pom", "alex"))
      assert(!cred.autoMatches("http://nexus.local/repository/a.pom", None))
      assert(!cred.autoMatches("https://nexus.local:8081/repository/a.pom", None))
    }

    test("IPv6 host") {
      val cred = DirectCredentials("[::1]:8081", "alex", "1234").copy(httpsOnly = false)
      assert(cred.autoMatches("http://[::1]:8081/a.pom", None))
      assert(!cred.autoMatches("http://[::1]:8082/a.pom", None))
    }
  }

}
