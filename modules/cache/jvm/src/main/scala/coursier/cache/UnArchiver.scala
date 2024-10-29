package coursier.cache

import java.io.{File, InputStream, OutputStream}
import java.nio.file.Files
import java.util.zip.GZIPInputStream

import org.codehaus.plexus.archiver.tar.{TarBZip2UnArchiver, TarGZipUnArchiver}
import org.codehaus.plexus.archiver.zip.ZipUnArchiver

trait UnArchiver {
  def extract(archiveType: ArchiveType, archive: File, destDir: File, overwrite: Boolean): Unit
}

object UnArchiver {
  private final class DefaultUnArchiver extends UnArchiver {

    def extract(
      archiveType: ArchiveType,
      archive: File,
      destDir: File,
      overwrite: Boolean
    ): Unit = {
      val unArchiver: Either[() => Unit, org.codehaus.plexus.archiver.UnArchiver] =
        archiveType match {
          case ArchiveType.Zip =>
            Right(new ZipUnArchiver)
          case ArchiveType.Tgz =>
            Right(new TarGZipUnArchiver)
          case ArchiveType.Tbz2 =>
            Right(new TarBZip2UnArchiver)
          case ArchiveType.Gzip =>
            Left { () =>
              // TODO Case-insensitive stripSuffix?
              val dest = new File(destDir, archive.getName.stripSuffix(".gz"))

              var fis: InputStream      = null
              var fos: OutputStream     = null
              var gzis: GZIPInputStream = null
              try {
                fis = Files.newInputStream(archive.toPath)
                gzis = new GZIPInputStream(fis)
                fos = Files.newOutputStream(dest.toPath)

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
