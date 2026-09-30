package coursier.cache

import java.util.concurrent.{ExecutorService, Executors}

import scala.cli.config.Secret
import utest._

import coursier.cache.TestUtil._
import coursier.util.Task

object CacheSubstitutionTests extends TestSuite {

  private def withExecutorService[T](pool: ExecutorService)(f: ExecutorService => T): T =
    try f(pool)
    finally pool.shutdownNow()

  val tests = Tests {

    test("substitute FileCache") {
      withTmpDir { dir =>
        withExecutorService(Executors.newFixedThreadPool(1)) { pool =>
          val logger = new CacheLogger {}
          val fileCache = FileCache[Task]((dir / "cache").toIO).copy(
            pool = pool,
            logger = logger,
            cachePolicies = Seq(CachePolicy.LocalOnly)
          )
          // same location, written differently
          val defaultCache = RemoteCache[Task]("http://localhost:1234", (dir / "cache").toIO)
            .copy(
              location = new java.io.File((dir / "other").toIO, "../cache"),
              basicAuth = Some(Secret("user:pass"))
            )

          val substituted = RemoteCache.substituteFor(fileCache, defaultCache).getOrElse {
            sys.error("Expected a substitute")
          }

          assert(substituted.serverUrl == defaultCache.serverUrl)
          assert(substituted.basicAuth == defaultCache.basicAuth)
          assert(substituted.location == fileCache.location)
          assert(substituted.pool eq pool)
          assert(substituted.logger eq logger)
          assert(substituted.cachePolicies == Seq(CachePolicy.LocalOnly))
          assert(substituted.fileFallback.exists(_ eq fileCache))
        }
      }
    }

    test("no substitution with a different location") {
      withTmpDir { dir =>
        val fileCache    = FileCache[Task]((dir / "cache").toIO)
        val defaultCache = RemoteCache[Task]("http://localhost:1234", (dir / "other").toIO)
        assert(RemoteCache.substituteFor(fileCache, defaultCache).isEmpty)
      }
    }

    test("no substitution with a FileCache default") {
      withTmpDir { dir =>
        val fileCache    = FileCache[Task]((dir / "cache").toIO)
        val defaultCache = FileCache[Task]((dir / "cache").toIO)
        assert(RemoteCache.substituteFor(fileCache, defaultCache).isEmpty)
      }
    }

    test("default local cache is never substituted") {
      assert(!Cache.defaultLocalCache.allowCacheSubstitution)
    }
  }
}
