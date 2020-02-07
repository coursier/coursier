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
    } finally {
      if (s != null)
        s.close()
    }
  }

  def auxName(name: String, auxExtension: String): String = {
    val (name0, _) = {
      val idx = name.lastIndexOf('.')
      if (idx >= 0)
        (name.take(idx), name.drop(idx))
      else
        (name, "")
    }

    s".$name0.aux$auxExtension"
  }

  def writing[T](baseDir: Path, dest: Path, auxExtension: String, verbosity: Int)(f: (Path, Path) => T): Option[T] = {

    // Ensuring we're the only process trying to write dest
    // - acquiring a lock while writing
    // - writing things to a temp file (tmpDest)
    // - atomic move to final dest, so that no borked launcher are exposed at any time, even during launcher generation
    //   Things these moves aren't actually atomic, because we move two files sometimes, and REPLACE_EXISTING doesn't
    //   seem to work along with ATOMIC_MOVE.

    val auxName0 = auxName(dest.getFileName.toString, auxExtension)

    val dir = dest.getParent

    val tmpDest = dir.resolve(s".${dest.getFileName}.part")
    val aux = dir.resolve(auxName0)
    val tmpAux = dir.resolve(s"$auxName0.part")

    def get: T = {
      Files.deleteIfExists(tmpDest)
      Files.deleteIfExists(tmpAux)

      val res = f(tmpDest, tmpAux)

      val updated = Files.isRegularFile(tmpDest) || Files.isRegularFile(tmpAux)

      if (Files.isRegularFile(tmpDest)) {

        if (verbosity >= 2) {
          System.err.println(s"Wrote $tmpDest")
          System.err.println(s"Moving $tmpDest to $dest")
        }
        Files.deleteIfExists(dest) // StandardCopyOption.REPLACE_EXISTING doesn't seem to work along with ATOMIC_MOVE
        Files.move(
          tmpDest,
          dest,
          StandardCopyOption.ATOMIC_MOVE
        )
        if (verbosity == 1)
          System.err.println(s"Wrote $dest")
      } else if (updated)
        Files.deleteIfExists(dest)

      if (Files.isRegularFile(tmpAux)) {
        if (verbosity >= 2) {
          System.err.println(s"Wrote $tmpAux")
          System.err.println(s"Moving $tmpAux to $aux")
        }
        Files.deleteIfExists(aux) // StandardCopyOption.REPLACE_EXISTING doesn't seem to work along with ATOMIC_MOVE
        Files.move(
          tmpAux,
          aux,
          StandardCopyOption.ATOMIC_MOVE
        )
        if (verbosity == 1)
          System.err.println(s"Wrote $aux")
      } else if (updated)
        Files.deleteIfExists(aux)

      res
    }

    CacheLocks.withLockOr(baseDir.toFile, dest.toFile)(Some(get), Some(None))
  }


}
