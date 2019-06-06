package coursier.cli.publish.util

import java.nio.file.{Files, Path}

import scala.collection.JavaConverters._

final class DeleteOnExit(verbosity: Int) {

  private def deleteRecursiveIfExists(f: Path): Unit = {

    if (Files.isDirectory(f)) {
      var s: java.util.stream.Stream[Path] = null
      try {
        s = Files.list(f)
        s.iterator()
          .asScala
          .foreach(deleteRecursiveIfExists)
      } finally {
        if (s != null)
          s.close()
      }
    }

    Files.deleteIfExists(f)
  }

  @volatile private var addedHook = false
  private val deleteOnExitLock = new Object
  private var deleteOnExit0 = List.empty[Path]

  def apply(f: Path): Unit = {

    if (!addedHook)
      deleteOnExitLock.synchronized {
        if (!addedHook) {
          Runtime.getRuntime.addShutdownHook(
            new Thread("coursier-publish-delete-on-exit") {
              setDaemon(true)
              override def run() =
                deleteOnExitLock.synchronized {
                  for (p <- deleteOnExit0.distinct if Files.exists(p)) {
                    if (verbosity >= 1)
                      Console.err.println(s"Cleaning up $p")
                    deleteRecursiveIfExists(p)
                  }
                }
            }
          )
          addedHook = true
        }
      }

    deleteOnExitLock.synchronized {
      deleteOnExit0 = f :: deleteOnExit0
    }
  }

}
