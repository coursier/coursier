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
    baseChangingOpt = Some(dataDir.resolve("changing")),
    writeMissing = updateSnapshots,
    replaceByNames = artifact =>
      artifact.url.startsWith("https://github.com/") &&
      artifact.url.contains("/releases/download/") &&
      (
        artifact.url.endsWith(".gz") ||
        artifact.url.endsWith(".zip") ||
        artifact.url.startsWith(
          "https://github.com/coursier/coursier/releases/download/v2.0.13/cs-"
        )
      ) &&
      !artifact.url.startsWith("https://github.com/sbt/sbtn-dist/releases/download/"),
    pool = pool
  )

}
