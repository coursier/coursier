package coursier.jvm

import java.io.File

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
        override def debug(message: String, throwable: Throwable) = ()
        override def info(message: String, throwable: Throwable) = ()
        override def warn(message: String, throwable: Throwable) = ()
        override def error(message: String, throwable: Throwable) = ()
        override def fatalError(message: String, throwable: Throwable) = ()
        override def getChildLogger(name: String) = this
      }

    def extract(archiveType: ArchiveType, archive: File, destDir: File, overwrite: Boolean): Unit = {
      val unArchiver: org.codehaus.plexus.archiver.UnArchiver =
        archiveType match {
          case ArchiveType.Zip =>
            val u = new ZipUnArchiver
            u.enableLogging(nopLogger())
            u
          case ArchiveType.Tgz =>
            val u = new TarGZipUnArchiver
            u.enableLogging(nopLogger())
            u
        }
      unArchiver.setOverwrite(false)
      unArchiver.setSourceFile(archive)
      unArchiver.setDestDirectory(destDir)

      destDir.mkdirs()
      unArchiver.extract()
    }
  }

  def default(): UnArchiver =
    new DefaultUnArchiver
}
