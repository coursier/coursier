package coursier.publish.dir

import java.nio.file.{Files, Path}

import coursier.publish.fileset.{FileSet, Path => FsPath}
import coursier.publish.Content
import coursier.publish.dir.logger.DirLogger
import coursier.util.Task

import scala.collection.JavaConverters._

object Dir {

  def fileSet(dir: Path, logger: DirLogger): Task[FileSet] = {

    def files(f: Path): Stream[Path] =
      if (Files.isRegularFile(f)) {
        logger.element(dir, f)
        Stream(f)
      } else if (Files.isDirectory(f)) {
        var s: java.util.stream.Stream[Path] = null
        try {
          s = Files.list(f)
          s.iterator()
            .asScala
            .toVector
            .toStream
            .flatMap(files)
        } finally {
          if (s != null)
            s.close()
        }
      } else
        // ???
        Stream()

    Task.delay {
      val dir0 = dir.normalize().toAbsolutePath
      val elems = files(dir0).toVector.map { f =>
        val p = FsPath(dir0.relativize(f).iterator().asScala.map(_.toString).toVector)
        val content = Content.File(f)
        (p, content)
      }
      FileSet(elems)
    }
  }

  def isRepository(dir: Path): Boolean = {

    def isMetadata(f: Path): Boolean = {
      val name = f.getFileName.toString
      name.endsWith(".pom") || name.endsWith(".xml")
    }

    // Some(false) if this directory or any of its sub-directories:
    //   - contains files,
    //   - but none of them looks like metadata (*.pom or *.xml)
    // Some(true) if this directory or any of its sub-directories:
    //   - contains files,
    //   - and all that do have files that look like metadata (*.pom or *.xml)
    // None else (no files found, only directories).
    def validate(f: Path): Option[Boolean] = {

      val (dirs, files) = {
        var s: java.util.stream.Stream[Path] = null
        try {
          s = Files.list(f)
          s.iterator().asScala.toVector.partition(Files.isDirectory(_))
        } finally {
          if (s != null)
            s.close()
        }
      }

      val checkFiles =
        if (files.isEmpty)
          None
        else
          Some(files.exists(isMetadata))

      // there should be a monoid for that…

      checkFiles match {
        case Some(false) => checkFiles
        case _ =>

          val checkDirs =
            dirs.foldLeft(Option.empty[Boolean]) {
              (acc, dir) =>
                acc match {
                  case Some(false) => acc
                  case _ =>
                    validate(dir) match {
                      case r @ Some(_) => r
                      case None => acc
                    }
                }
            }

          checkDirs match {
            case Some(_) => checkDirs
            case None => checkFiles
          }
      }
    }

    Files.isDirectory(dir) && {
      validate(dir).getOrElse(false)
    }
  }

  def read(dir: Path, logger: => DirLogger): Task[FileSet] = {

    val before = Task.delay {
      val logger0 = logger
      logger0.start()
      logger0.reading(dir)
      logger0
    }
    def after(count: Int, logger0: DirLogger) = Task.delay {
      logger0.read(dir, count)
      logger0.stop()
    }

    for {
      logger0 <- before
      a <- fileSet(dir, logger0).attempt
      _ <- after(a.toOption.fold(0)(_.elements.length), logger0)
      fs <- Task.fromEither(a)
    } yield fs
  }

}
