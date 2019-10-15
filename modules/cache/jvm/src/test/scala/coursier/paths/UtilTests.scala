package coursier.paths

import java.io.File
import java.nio.file.{Files, Path}

import utest._

object UtilTests extends TestSuite {

  private def deleteRecursive(f: File): Unit = {
    if (f.isDirectory)
      f.listFiles().foreach(deleteRecursive)
    if (f.exists())
      f.delete()
  }

  val tests = Tests {
    "createDirectories fine with sym links" - {
      var tmpDir: Path = null
      try {
        tmpDir = Files.createTempDirectory("coursier-paths-tests")
        val dir = Files.createDirectories(tmpDir.resolve("dir"))
        val link = Files.createSymbolicLink(tmpDir.resolve("link"), dir)
        Util.createDirectories(link) // should not throw
      } finally {
        deleteRecursive(tmpDir.toFile)
      }
    }
  }

}
