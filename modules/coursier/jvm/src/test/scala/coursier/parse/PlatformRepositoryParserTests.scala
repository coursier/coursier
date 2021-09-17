package coursier.parse

import coursier.LocalRepositories
import utest._

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
  }

}
