package coursier

import utest._

import scala.async.Async.{async, await}

object VersionsTests extends TestSuite {

  import TestHelpers.{ec, cache}

  private val versions = Versions()
    .withCache(cache)

  val tests = Tests {
    "simple" - async {
      val shapelessVersions = await(versions.withModule(mod"com.chuusai:shapeless_2.12").versions().future).available
      val expectedShapelessVersions = Seq("2.3.2", "2.3.3")
      assert(shapelessVersions == expectedShapelessVersions)
    }
  }

}
