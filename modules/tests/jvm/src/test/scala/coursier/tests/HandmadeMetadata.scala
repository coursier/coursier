package coursier.tests

import java.io.File

object HandmadeMetadata {

  private lazy val originalRepoBase = {
    val dirStr = Option(System.getenv("COURSIER_TESTS_HANDMADE_METADATA_DIR")).getOrElse {
      sys.error("COURSIER_TESTS_HANDMADE_METADATA_DIR not set")
    }
    val dir = os.Path(dirStr, os.pwd)
    assert(os.isDir(dir))
    dir
  }

  // Some tests use this directory as a cache, and write things in it (checksums computed by
  // FileCache, say). Work off a temporary copy, so that the original directory, which is under
  // version control, isn't modified.
  lazy val repoBase: File = {
    // not relying on os.temp.dir's deleteOnExit, that only wipes out empty directories
    val tmpDir = os.temp.dir(prefix = "coursier-handmade-metadata", deleteOnExit = false)
    Runtime.getRuntime.addShutdownHook(
      new Thread("clean-up-handmade-metadata") {
        override def run(): Unit =
          os.remove.all(tmpDir)
      }
    )
    os.copy.over(originalRepoBase, tmpDir)
    tmpDir.toIO
  }
}
