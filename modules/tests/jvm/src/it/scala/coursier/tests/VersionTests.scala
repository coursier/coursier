package coursier.tests

import coursier.Versions
import coursier.cache.FileCache
import coursier.core.{Module, ModuleName, Organization}
import utest.{TestSuite, Tests, assert, test}

import scala.async.Async.{async, await}
import scala.concurrent.ExecutionContextExecutorService

object VersionTests extends TestSuite {
  implicit val ec: ExecutionContextExecutorService = FileCache().ec
  val tests: Tests = Tests {
    test {
      async {
        val csModule =
          Module(
            Organization("com.lihaoyi"),
            ModuleName("os-lib_3"),
            Map.empty
          )
        val versions: coursier.core.Versions =
          await {
            Versions()
              .withModule(csModule)
              .result()
              .future()
          }.versions
        val legacyLatest: Option[String] =
          versions.latest(coursier.core.Latest.Stable)
        val newLatest: Option[String] =
          versions.latest(coursier.version.Latest.Stable).map(_.asString)
        assert(legacyLatest.nonEmpty)
        assert(newLatest.nonEmpty)
        assert(legacyLatest == newLatest)
      }
    }
  }
}
