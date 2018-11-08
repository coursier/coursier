package coursier.cli.publish.checksum

import java.nio.charset.StandardCharsets
import java.time.Instant

import coursier.cli.publish.Content
import coursier.cli.publish.fileset.FileSet
import coursier.util.Task

object Checksums {

  /**
    * Compute the missing checksums in a [[FileSet]].
    *
    * @param types: checksum types to check / compute
    * @param fileSet: initial [[FileSet]], can optionally contain some already calculated checksum
    * @param now: last modified time for the added checksum files
    * @return a [[FileSet]] of the missing checksum files
    */
  def apply(
    types: Seq[ChecksumType],
    fileSet: FileSet,
    now: Instant,
    logger: ChecksumLogger
  ): Task[FileSet] = {

    // separate base files from existing checksums
    val filesOrChecksums = fileSet
      .elements
      .map {
        case (path, content) =>
          types
            .collectFirst {
              case t if path.elements.lastOption.exists(_.endsWith("." + t.extension)) =>
                (path.mapLast(_.stripSuffix("." + t.extension)), t)
            }
            .toLeft((path, content))
      }

    val checksums = filesOrChecksums
      .collect {
        case Left(e) => e
      }
      .toSet

    val files = filesOrChecksums
      .collect {
        case Right(p) => p
      }

    val missing =
      for {
        type0 <- types
        (path, content) <- files
        if !checksums((path, type0))
      } yield (type0, path, content)

    // compute missing checksum files
    def checksumFilesTask(id: Object) = Task.gather.gather {
      for ((type0, path, content) <- missing)
        yield {
          val checksumPath = path.mapLast(_ + "." + type0.extension)
          val doSign = content.contentTask.map { b =>
            val checksum = Checksum.compute(type0, b)
            (checksumPath, Content.InMemory(now, checksum.repr.getBytes(StandardCharsets.UTF_8)))
          }
          for {
            _ <- Task.delay(logger.computing(id, type0, checksumPath.repr))
            a <- doSign.attempt
            _ <- Task.delay(logger.computed(id, type0, checksumPath.repr, a.left.toOption))
            res <- Task.fromEither(a)
          } yield res
        }
    }.map { elements =>
      FileSet(elements)
    }

    val missingFs = FileSet(
      missing.map {
        case (type0, path, content) =>
          val checksumPath = path.mapLast(_ + "." + type0.extension)
          (checksumPath, content) // kind of meh… content isn't used here anyway
      }
    )

    val before = Task.delay {
      val id = new Object
      logger.start()
      logger.computingSet(id, missingFs)
      id
    }

    def after(id: Object) = Task.delay {
      logger.computedSet(id, missingFs)
      logger.stop()
    }

    for {
      id <- before
      a <- checksumFilesTask(id).attempt
      _ <- after(id)
      res <- Task.fromEither(a)
    } yield res
  }

}
