package coursier

import utest._

import scala.async.Async.{async, await}

object VersionsTests extends TestSuite {

  import TestHelpers.{ec, cache}

  private val versions = Versions()
    .withCache(cache)

  val tests = Tests {
    "simple" - async {
      val shapelessVersions =
        await(versions.withModule(mod"com.chuusai:shapeless_2.12").versions().future()).available
      val expectedShapelessVersions =
        Seq("2.3.2", "2.3.3", "2.3.4-M1", "2.3.4", "2.3.5", "2.3.6", "2.3.7", "2.4.0-M1")
      assert(shapelessVersions == expectedShapelessVersions)
    }
  }

}
