package coursier.install

import java.nio.channels.{FileChannel, FileLock}
import java.nio.file.{Files, Path, StandardOpenOption, StandardCopyOption}
import java.util.stream.Stream

import coursier.cache.CacheLocks
import coursier.launcher.internal.FileUtil
import coursier.paths.CachePath

import scala.collection.JavaConverters._
import scala.util.control.NonFatal

object Updatable {

  def list(dir: Path): Seq[String] = {

    var s: Stream[Path] = null
    try {
      s = Files.list(dir)
      s.iterator()
        .asScala
        .filter(p => !p.getFileName.toString.startsWith("."))
        .filter(InfoFile.isInfoFile)
        .map(_.getFileName.toString)
        .toVector
        .sorted
    } finally {
      if (s != null)
        s.close()
    }
  }

  private final case class RelatedFiles(
    dest: Path,
    aux: Path,
    tmpDest: Path,
    tmpAux: Path
  ) {
    def list: Seq[Path] =
      Seq(dest, aux, tmpDest, tmpAux)
  }

  private def relatedFiles(dest: Path, auxExtension: String): RelatedFiles = {

    val auxName0 = InstallDir.auxName(dest.getFileName.toString, auxExtension)

    val dir = dest.getParent

    val tmpDest = dir.resolve(s".${dest.getFileName}.part")
    val aux = dir.resolve(auxName0)
    val tmpAux = dir.resolve(s"$auxName0.part")

    RelatedFiles(dest, aux, tmpDest, tmpAux)
  }

  def writing[T](baseDir: Path, dest: Path, auxExtension: String, verbosity: Int)(f: (Path, Path) => T): Option[T] = {

    // Ensuring we're the only process trying to write dest
    // - acquiring a lock while writing
    // - writing things to a temp file (tmpDest)
    // - atomic move to final dest, so that no borked launcher are exposed at any time, even during launcher generation
    //   Things these moves aren't actually atomic, because we move two files sometimes, and REPLACE_EXISTING doesn't
    //   seem to work along with ATOMIC_MOVE.

    val files = relatedFiles(dest, auxExtension)

    val dir = dest.getParent

    def get: T = {
      Files.deleteIfExists(files.tmpDest)
      Files.deleteIfExists(files.tmpAux)

      val res = f(files.tmpDest, files.tmpAux)

      val updated = Files.isRegularFile(files.tmpDest) || Files.isRegularFile(files.tmpAux)

      if (Files.isRegularFile(files.tmpDest)) {

        if (verbosity >= 2) {
          System.err.println(s"Wrote ${files.tmpDest}")
          System.err.println(s"Moving ${files.tmpDest} to $dest")
        }
        Files.deleteIfExists(dest) // StandardCopyOption.REPLACE_EXISTING doesn't seem to work along with ATOMIC_MOVE
        Files.move(
          files.tmpDest,
          dest,
          StandardCopyOption.ATOMIC_MOVE
        )
        if (verbosity == 1)
          System.err.println(s"Wrote $dest")
      } else if (updated)
        Files.deleteIfExists(dest)

      if (Files.isRegularFile(files.tmpAux)) {
        if (verbosity >= 2) {
          System.err.println(s"Wrote ${files.tmpAux}")
          System.err.println(s"Moving ${files.tmpAux} to ${files.aux}")
        }
        FileUtil.tryHideWindows(files.tmpAux)
        Files.deleteIfExists(files.aux) // StandardCopyOption.REPLACE_EXISTING doesn't seem to work along with ATOMIC_MOVE
        Files.move(
          files.tmpAux,
          files.aux,
          StandardCopyOption.ATOMIC_MOVE
        )
        if (verbosity == 1)
          System.err.println(s"Wrote ${files.aux}")
      } else if (updated)
        Files.deleteIfExists(files.aux)

      res
    }

    CacheLocks.withLockOr(baseDir.toFile, dest.toFile)(Some(get), Some(None))
  }

  def delete[T](baseDir: Path, dest: Path, auxExtension: String, verbosity: Int): Option[Boolean] = {

    if (InfoFile.isInfoFile(dest)) {

      val files = relatedFiles(dest, auxExtension)

      def get: Boolean = {

        val foundSomething = files.list.exists(Files.exists(_))

        foundSomething && {
          files.list.foreach(Files.deleteIfExists(_))
          true
        }
      }

      CacheLocks.withLockOr(baseDir.toFile, dest.toFile)(Some(get), Some(None))
    } else
      throw new InstallDir.NotAnApplication(dest)
  }

}
