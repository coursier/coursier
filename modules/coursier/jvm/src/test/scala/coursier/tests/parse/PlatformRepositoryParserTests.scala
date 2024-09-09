package coursier.tests.parse

import coursier.{LocalRepositories, Repositories}
import coursier.maven.MavenRepository
import coursier.parse._
import utest._
import coursier.core.Authentication

object PlatformRepositoryParserTests extends TestSuite {

  val tests = Tests {
    test("m2Local") - {
      test {
        val res         = RepositoryParser.repository("m2Local")
        val expectedRes = Right(LocalRepositories.Dangerous.maven2Local)
        assert(res == expectedRes)
      }

      test {
        val res         = RepositoryParser.repository("m2local")
        val expectedRes = Right(LocalRepositories.Dangerous.maven2Local)
        assert(res == expectedRes)
      }
    }

    test("Maven Central") - {
      val res         = RepositoryParser.repository("https://repo1.maven.org/maven2")
      val expectedRes = Right(Repositories.central)
      assert(res == expectedRes)
    }

    test("AWS codeartifact with password") - {
      val res = RepositoryParser.repository(
        "https://aws:pass@domain.d.codeartifact.us-east-1.amazonaws.com/maven/dir"
      )
      val expectedRes = Right(MavenRepository(
        "https://domain.d.codeartifact.us-east-1.amazonaws.com/maven/dir"
      ).withAuthentication(Some(Authentication("aws", "pass"))))
      assert(res == expectedRes)
    }

    test("AWS codeartifact without password") - {
      val res = RepositoryParser.repository(
        "https://aws@domain.d.codeartifact.us-east-1.amazonaws.com/maven/dir"
      )
      val expectedRes = Right(MavenRepository(
        "https://domain.d.codeartifact.us-east-1.amazonaws.com/maven/dir"
      ).withAuthentication(Some(Authentication("aws"))))
      assert(res == expectedRes)
    }
  }

}
