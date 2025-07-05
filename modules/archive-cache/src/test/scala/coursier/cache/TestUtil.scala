package coursier.cache

import java.io.File
import java.nio.file.{Files, Path}

object TestUtil {

  private def deleteRecursive(f: File): Unit = {
    if (f.isDirectory)
      f.listFiles().foreach(deleteRecursive)
    f.delete()
  }

  def withTmpDir[T](f: os.Path => T): T = {
    val dir = os.temp.dir(prefix = "coursier-test")
    try f(dir)
    finally os.remove.all(dir)
  }

  def withTmpDir0[T](f: Path => T): T = {
    val dir                  = Files.createTempDirectory("coursier-test")
    val shutdownHook: Thread =
      new Thread {
        override def run() =
          deleteRecursive(dir.toFile)
      }
    Runtime.getRuntime.addShutdownHook(shutdownHook)
    try f(dir)
    finally {
      deleteRecursive(dir.toFile)
      Runtime.getRuntime.removeShutdownHook(shutdownHook)
    }
  }
}
