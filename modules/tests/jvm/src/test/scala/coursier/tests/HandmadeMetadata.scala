package coursier.tests

import java.nio.file.{Files, Paths}

object HandmadeMetadata {
  val repoBase = {
    val dirStr = Option(System.getenv("COURSIER_TESTS_HANDMADE_METADATA_DIR")).getOrElse {
      sys.error("COURSIER_TESTS_HANDMADE_METADATA_DIR not set")
    }
    val dir = Paths.get(dirStr)
    assert(Files.isDirectory(dir))
    dir.toFile
  }
}
