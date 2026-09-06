package coursierapi

import coursier.{LocalRepositories, Repositories}
import coursier.internal.api.ApiHelper
import scala.collection.JavaConverters._
import utest._

object RepositoryTests extends TestSuite {

  val tests = Tests {

    test("maven") {
      val initialRepo = MavenRepository.of("https://artifacts.corp.com")

      test("simple") {
        val repo  = initialRepo
        val repo0 = ApiHelper.repository(ApiHelper.repository(repo))
        assert(repo == repo0)
      }

      test("credentials") {
        val repo = MavenRepository.of(initialRepo)
          .withCredentials(Credentials.of("a", "1234"))
        val repo0 = ApiHelper.repository(ApiHelper.repository(repo))
        assert(repo != initialRepo)
        assert(repo == repo0)
      }

      test("ivy2Local") {
        val toFromIvy2Local = ApiHelper.repository(Repository.ivy2Local())
        val ivy2Local       = LocalRepositories.ivy2Local
        assert(ivy2Local == toFromIvy2Local)
      }

      test("central") {
        val toFromCentral = ApiHelper.repository(Repository.central())
        val central       = Repositories.central
        assert(central == toFromCentral)
      }
    }

    test("parser") {
      test("central") {
        val parsed   = ApiHelper.repository(RepositoryParser.repository("central"))
        val expected = coursier.parse.RepositoryParser.repository("central").toOption.get
        assert(parsed == expected)
      }

      test("ivy2Local") {
        val parsed   = ApiHelper.repository(RepositoryParser.repository("ivy2Local"))
        val expected = coursier.parse.RepositoryParser.repository("ivy2Local").toOption.get
        assert(parsed == expected)
      }

      test("ivyPattern") {
        val input    = "ivy:https://repo/[organisation]/[module]/[revision]/[artifact].[ext]"
        val parsed   = ApiHelper.repository(RepositoryParser.repository(input))
        val expected = coursier.parse.RepositoryParser.repository(input).toOption.get
        assert(parsed == expected)
      }

      test("invalidSingle") {
        val input = "ivy:[unclosed"
        assertThrows[IllegalArgumentException] {
          RepositoryParser.repository(input)
        }
      }

      test("batch") {
        val inputs = List("central", "ivy2Local")
        val parsed = RepositoryParser.repositories(inputs.asJava).asScala.map(ApiHelper.repository)
        val expected = inputs.map(s => coursier.parse.RepositoryParser.repository(s).toOption.get)
        assert(parsed.toList == expected)
      }

      test("invalidBatch") {
        val ex = assertThrows[coursierapi.error.RepositoryParsingError] {
          RepositoryParser.repositories(List("ivy:[unclosed", "ivy:[alsoUnclosed").asJava)
        }
        assert(ex.getErrors.size() == 2)
      }
    }

  }

}
