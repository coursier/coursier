package coursier.tests

import coursier.Versions
import coursier.util.StringInterpolators._
import coursier.version.Version
import utest._

import scala.async.Async.{async, await}

object VersionsTests extends TestSuite {

  import TestHelpers.{ec, cache}

  private val versions = Versions()
    .withCache(cache)

  val tests = Tests {
    test("simple") {
      async {
        val shapelessVersions =
          await(versions.withModule(mod"com.chuusai:shapeless_2.12").versions().future()).available0
        val expectedShapelessVersions = Seq(
          Version("2.3.2"),
          Version("2.3.3"),
          Version("2.3.4-M1"),
          Version("2.3.4"),
          Version("2.3.5"),
          Version("2.3.6"),
          Version("2.3.7"),
          Version("2.3.8"),
          Version("2.3.9"),
          Version("2.3.10"),
          Version("2.3.11"),
          Version("2.3.12"),
          Version("2.3.13"),
          Version("2.4.0-M1")
        )
        assert(shapelessVersions == expectedShapelessVersions)
      }
    }
  }

}
