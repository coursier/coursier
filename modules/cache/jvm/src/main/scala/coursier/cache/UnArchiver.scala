package coursier.cache

import java.io.{File, FileInputStream, FileOutputStream}
import java.util.zip.GZIPInputStream

import org.codehaus.plexus.archiver.tar.TarGZipUnArchiver
import org.codehaus.plexus.archiver.zip.ZipUnArchiver
import org.codehaus.plexus.logging.{AbstractLogger, Logger}

trait UnArchiver {
  def extract(archiveType: ArchiveType, archive: File, destDir: File, overwrite: Boolean): Unit
}

object UnArchiver {
  private final class DefaultUnArchiver extends UnArchiver {

    private def nopLogger(): Logger =
      new AbstractLogger(Logger.LEVEL_DISABLED, "foo") {
        override def debug(message: String, throwable: Throwable)      = ()
        override def info(message: String, throwable: Throwable)       = ()
        override def warn(message: String, throwable: Throwable)       = ()
        override def error(message: String, throwable: Throwable)      = ()
        override def fatalError(message: String, throwable: Throwable) = ()
        override def getChildLogger(name: String)                      = this
      }

    def extract(
      archiveType: ArchiveType,
      archive: File,
      destDir: File,
      overwrite: Boolean
    ): Unit = {
      val unArchiver: Either[() => Unit, org.codehaus.plexus.archiver.UnArchiver] =
        archiveType match {
          case ArchiveType.Zip =>
            val u = new ZipUnArchiver
            u.enableLogging(nopLogger())
            Right(u)
          case ArchiveType.Tgz =>
            val u = new TarGZipUnArchiver
            u.enableLogging(nopLogger())
            Right(u)
          case ArchiveType.Gzip =>
            Left { () =>
              // TODO Case-insensitive stripSuffix?
              val dest = new File(destDir, archive.getName.stripSuffix(".gz"))

              var fis: FileInputStream  = null
              var fos: FileOutputStream = null
              var gzis: GZIPInputStream = null
              try {
                fis = new FileInputStream(archive)
                gzis = new GZIPInputStream(fis)
                fos = new FileOutputStream(dest)

                val buf  = Array.ofDim[Byte](16 * 1024)
                var read = -1
                while ({
                  read = gzis.read(buf)
                  read >= 0
                })
                  if (read > 0)
                    fos.write(buf, 0, read)
                fos.flush()
              }
              finally {
                if (gzis != null) gzis.close()
                if (fos != null) fos.close()
                if (fis != null) fis.close()
              }
            }
        }

      destDir.mkdirs()

      unArchiver match {
        case Left(f) =>
          f()
        case Right(u) =>
          u.setOverwrite(false)
          u.setSourceFile(archive)
          u.setDestDirectory(destDir)
          u.extract()
      }
    }
  }

  def default(): UnArchiver =
    new DefaultUnArchiver
}
