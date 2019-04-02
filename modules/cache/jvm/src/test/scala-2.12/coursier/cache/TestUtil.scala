package coursier.cache

import java.io.File
import java.nio.file.{Files, Path}

object TestUtil {

  private def deleteRecursive(f: File): Unit = {
    if (f.isDirectory)
      f.listFiles().foreach(deleteRecursive)
    f.delete()
  }

  def withTmpDir[T](f: Path => T): T = {
    val dir = Files.createTempDirectory("coursier-test")
    try f(dir)
    finally {
      deleteRecursive(dir.toFile)
    }
  }

}
