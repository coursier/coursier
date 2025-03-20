package coursier.testcache

import java.nio.file.{Files, Paths}
import coursier.cache.MockCache
import coursier.util.Sync

object TestCache {

  lazy val pool = Sync.fixedThreadPool(6)

  lazy val dataDir = {
    val dirStr = Option(System.getenv("COURSIER_TESTS_METADATA_DIR")).getOrElse {
      sys.error("COURSIER_TESTS_METADATA_DIR not set")
    }
    val dir = Paths.get(dirStr)
    assert(Files.isDirectory(dir))
    assert(
      Files.isDirectory(dir),
      s"we're in ${Paths.get(".").toAbsolutePath.normalize}, no $dir here"
    )
    dir
  }

  val updateSnapshots = Option(System.getenv("FETCH_MOCK_DATA"))
    .exists(s => s == "1" || s == "true")

  def cache[F[_]: Sync] = MockCache.create(
    dataDir,
    writeMissing = updateSnapshots,
    pool = pool
  )

}
