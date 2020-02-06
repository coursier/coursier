package coursier.install

import java.io.FileOutputStream
import java.nio.charset.StandardCharsets
import java.nio.file.attribute.FileTime
import java.nio.file.{Files, Path}
import java.time.Instant
import java.util.zip.{ZipEntry, ZipFile, ZipOutputStream}

import coursier.cache.internal.FileUtil
import coursier.launcher.Preamble

import scala.util.control.NonFatal

object InfoFile {

  private[coursier] def jsonSourceFilePath = "META-INF/coursier/info-source.json"
  private[coursier] def jsonDescFilePath = "META-INF/coursier/info.json"
  private[coursier] def lockFilePath = "META-INF/coursier/lock-file"
  private[coursier] def sharedLockFilePath = "META-INF/coursier/shared-deps-lock-file"


  def isInfoFile(p: Path): Boolean =
    ???

  def readSource(f: Path): Option[(Source, Array[Byte])] = {

    var zf: ZipFile = null

    try {
      zf = new ZipFile(f.toFile)
      val entOpt = Option(zf.getEntry(jsonSourceFilePath))

      entOpt.map { ent =>
        val content = FileUtil.readFully(zf.getInputStream(ent))
        val e = RawSource.parse(new String(content, StandardCharsets.UTF_8))
          .left.map(err => new ErrorParsingSource(s"$f!$jsonSourceFilePath", err))
          .flatMap { r =>
            r.source
              .toEither
              .left.map { errors =>
                new ErrorProcessingSource(s"$f!$jsonSourceFilePath", errors.toList.mkString(", "))
              }
          }
        val source = e.fold(throw _, identity)
        (source, content)
      }
    } catch {
      case NonFatal(e) =>
        throw new Exception(s"Reading $f", e)
    } finally {
      if (zf != null)
        zf.close()
    }
  }

  def readAppDescriptor(f: Path): Option[(AppDescriptor, Array[Byte])] = {

    val from = {
      val info = f.getParent.resolve(s".${f.getFileName}.info")
      if (Files.isRegularFile(info))
        info
      else
        f
    }

    var zf: ZipFile = null

    try {
      zf = new ZipFile(from.toFile)
      val entOpt = Option(zf.getEntry(jsonDescFilePath))

      entOpt.map { ent =>
        val content = FileUtil.readFully(zf.getInputStream(ent))
        val e = RawAppDescriptor.parse(new String(content, StandardCharsets.UTF_8))
          .left.map(err => new ErrorParsingAppDescription(s"$from!$jsonDescFilePath", err))
          .flatMap { r =>
            r.appDescriptor
              .toEither
              .left.map { errors =>
                new ErrorProcessingAppDescription(s"$from!$jsonDescFilePath", errors.toList.mkString(", "))
              }
          }
        val desc = e.fold(throw _, identity)
        (desc, content)
      }
    } catch {
      case NonFatal(e) =>
        throw new Exception(s"Reading $from", e)
    } finally {
      if (zf != null)
        zf.close()
    }
  }

  def extraEntries(
    lock: ArtifactsLock,
    sharedLockOpt: Option[ArtifactsLock],
    descRepr: Array[Byte],
    sourceReprOpt: Option[Array[Byte]],
    currentTime: Instant
  ): Seq[(ZipEntry, Array[Byte])] = {

    val lockFileEntry = {
      val e = new ZipEntry(lockFilePath)
      e.setLastModifiedTime(FileTime.from(currentTime))
      e.setCompressedSize(-1L)
      val content = lock.repr.getBytes(StandardCharsets.UTF_8)
      e -> content
    }

    val sharedLockFileEntryOpt = sharedLockOpt.map { lock0 =>
      val e = new ZipEntry(sharedLockFilePath)
      e.setLastModifiedTime(FileTime.from(currentTime))
      e.setCompressedSize(-1L)
      val content = lock0.repr.getBytes(StandardCharsets.UTF_8)
      e -> content
    }

    val destEntry = {
      val e = new ZipEntry(jsonDescFilePath)
      e.setLastModifiedTime(FileTime.from(currentTime))
      e.setCompressedSize(-1L)
      e -> descRepr
    }

    val sourceEntryOpt = sourceReprOpt.map { sourceRepr =>
      val e = new ZipEntry(jsonSourceFilePath)
      e.setLastModifiedTime(FileTime.from(currentTime))
      e.setCompressedSize(-1L)
      e -> sourceRepr
    }

    Seq(destEntry, lockFileEntry) ++ sourceEntryOpt.toSeq ++ sharedLockFileEntryOpt.toSeq
  }

  def upToDate(
    infoFile: Path,
    lock: ArtifactsLock,
    sharedLockOpt: Option[ArtifactsLock],
    descRepr: Array[Byte],
    sourceReprOpt: Option[Array[Byte]]
  ) = Files.exists(infoFile) && {

    var f: ZipFile = null
    try {
      f = new ZipFile(infoFile.toFile)
      val lockEntryOpt = Option(f.getEntry(lockFilePath))
      val sharedLockEntryOpt = Option(f.getEntry(sharedLockFilePath))
      val descFileEntryOpt = Option(f.getEntry(jsonDescFilePath))
      val sourceFileEntryOpt = Option(f.getEntry(jsonSourceFilePath))

      def read(ent: ZipEntry): Array[Byte] =
        FileUtil.readFully(f.getInputStream(ent))

      def readLock(ent: ZipEntry): ArtifactsLock = {
        // FIXME Don't just throw in case of malformed file?
        val s = new String(read(ent), StandardCharsets.UTF_8)
        ArtifactsLock.read(s) match {
          case Left(err) => throw new ErrorReadingLockFile(s"$infoFile!${ent.getName}", err)
          case Right(l) => l
        }
      }

      val initialAppDesc = descFileEntryOpt.map(read(_).toSeq)
      val initialSource = sourceFileEntryOpt.map(read(_).toSeq)

      val initialLockOpt: Option[ArtifactsLock] = lockEntryOpt.map(readLock)
      val initialSharedLockOpt: Option[ArtifactsLock] = sharedLockEntryOpt.map(readLock)

      initialLockOpt.contains(lock) &&
        initialSharedLockOpt == sharedLockOpt &&
        initialAppDesc.contains(descRepr.toSeq) &&
        initialSource == sourceReprOpt.map(_.toSeq)
    } catch {
      case NonFatal(e) =>
        throw new Exception(s"Reading $infoFile", e)
    } finally {
      if (f != null)
        f.close()
    }
  }

  def writeInfoFile(dest: Path, preamble: Option[Preamble], entries: Seq[(ZipEntry, Array[Byte])]): Unit = {
    val preambleDataOpt = preamble.map(_.value)

    var fos: FileOutputStream = null
    var zos: ZipOutputStream = null
    try {
      fos = new FileOutputStream(dest.toFile)

      for (b <- preambleDataOpt)
        fos.write(b)

      zos = new ZipOutputStream(fos)
      for ((ent, b) <- entries) {
        zos.putNextEntry(ent)
        zos.write(b)
        zos.closeEntry()
      }
    } finally {
      if (zos != null)
        zos.close()
      if (fos != null)
        fos.close()
    }
  }



  sealed abstract class InfoFileException(message: String, cause: Throwable = null)
    extends Exception(message, cause)

  final class ErrorParsingSource(path: String, details: String)
    extends InfoFileException(s"Error parsing source $path: $details")
  final class ErrorProcessingSource(path: String, details: String)
    extends InfoFileException(s"Error processing source $path: $details")

  final class ErrorParsingAppDescription(path: String, details: String)
    extends InfoFileException(s"Error parsing app description $path: $details")
  final class ErrorProcessingAppDescription(path: String, details: String)
    extends InfoFileException(s"Error processing app description $path: $details")

  final class ErrorReadingLockFile(path: String, details: String)
    extends InfoFileException(s"Error reading lock file $path: $details")

}
