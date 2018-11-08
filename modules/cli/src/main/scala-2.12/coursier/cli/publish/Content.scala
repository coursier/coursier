package coursier.cli.publish

import java.nio.file.{Files, Path}
import java.time.Instant

import coursier.util.Task

/**
  * Content of a file, either on disk or in memory.
  */
sealed abstract class Content extends Product with Serializable {
  def lastModifiedTask: Task[Instant]
  // TODO Support chunked reading
  def contentTask: Task[Array[Byte]]

  def pathOpt: Option[Path] = None
}

object Content {

  final case class File(path: Path) extends Content {
    def lastModifiedTask: Task[Instant] =
      Task.delay {
        Files.getLastModifiedTime(path)
          .toInstant
      }
    def contentTask: Task[Array[Byte]] =
      Task.delay {
        Files.readAllBytes(path)
      }
    override def pathOpt: Option[Path] =
      Some(path)
  }

  final case class InMemory(lastModified: Instant, content: Array[Byte]) extends Content {
    def lastModifiedTask: Task[Instant] =
      Task.point(lastModified)
    def contentTask: Task[Array[Byte]] =
      Task.point(content)
  }

}
