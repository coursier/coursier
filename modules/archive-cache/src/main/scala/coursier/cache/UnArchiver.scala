package coursier.cache

import org.apache.commons.compress.archivers.ar.{ArArchiveEntry, ArArchiveInputStream}
import org.apache.commons.compress.compressors.xz.XZCompressorInputStream
import org.apache.commons.io.input.{BoundedInputStream, CountingInputStream}
import org.codehaus.plexus.archiver.ArchiverException
import org.codehaus.plexus.archiver.tar.{
  TarBZip2UnArchiver,
  TarGZipUnArchiver,
  TarXZUnArchiver,
  TarZstdUnArchiver,
  TarUnArchiver
}
import org.codehaus.plexus.archiver.zip.ZipUnArchiver
import org.codehaus.plexus.components.io.resources.PlexusIoResource

import java.io.{BufferedInputStream, File, IOException, InputStream, OutputStream}
import java.nio.file.Files
import java.util.zip.GZIPInputStream

import scala.jdk.CollectionConverters._
import scala.util.Using

trait UnArchiver {
  def extract(archiveType: ArchiveType, archive: File, destDir: File, overwrite: Boolean): Unit
}

object UnArchiver {
  trait OpenStream {
    def inputStream(archiveType: ArchiveType.Compressed, is: InputStream): InputStream
  }

  private final class DefaultUnArchiver extends UnArchiver with OpenStream {

    def extractCompressed(
      archiveType: ArchiveType.Compressed,
      archive: File,
      destDir: File
    ): () => Unit =
      archiveType match {
        case ArchiveType.Gzip =>
          () => {
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
        case ArchiveType.Xz =>
          () => {
            // TODO Case-insensitive stripSuffix?
            val dest = new File(destDir, archive.getName.stripSuffix(".xz"))

            var fis: InputStream             = null
            var fos: OutputStream            = null
            var xis: XZCompressorInputStream = null
            try {
              fis = Files.newInputStream(archive.toPath)
              xis = new XZCompressorInputStream(fis)
              fos = Files.newOutputStream(dest.toPath)

              val buf  = Array.ofDim[Byte](16 * 1024)
              var read = -1
              while ({
                read = xis.read(buf)
                read >= 0
              })
                if (read > 0)
                  fos.write(buf, 0, read)
              fos.flush()
            }
            finally {
              if (xis != null) xis.close()
              if (fos != null) fos.close()
              if (fis != null) fis.close()
            }
          }
      }

    def extract(
      archiveType: ArchiveType,
      archive: File,
      destDir: File,
      overwrite: Boolean
    ): Unit = {
      val unArchiver: Either[() => Unit, org.codehaus.plexus.archiver.UnArchiver] =
        archiveType match {
          case c: ArchiveType.Compressed =>
            Left(extractCompressed(c, archive, destDir))
          case ArchiveType.Zip =>
            Right(new ZipUnArchiver)
          case ArchiveType.Ar =>
            val unArc: org.codehaus.plexus.archiver.UnArchiver =
              new org.codehaus.plexus.archiver.AbstractUnArchiver {
                def fileInfo(entry: ArArchiveEntry): PlexusIoResource =
                  new PlexusIoResource {
                    def getName         = entry.getName
                    def isSymbolicLink  = false
                    def getContents     = ???
                    def getLastModified = entry.getLastModified
                    def getSize         = entry.getSize
                    def getURL          = null
                    def isDirectory     = entry.isDirectory
                    def isExisting      = true
                    def isFile          = !isDirectory
                  }
                def execute(): Unit = execute("", getDestDirectory)
                // based on org.codehaus.plexus.archiver.zip.AbstractZipUnArchiver
                def execute(path: String, outputDirectory: File): Unit =
                  try
                    Using.resource(Files.newInputStream(getSourceFile.toPath)) { fis =>
                      val ais = new ArArchiveInputStream(new BufferedInputStream(fis))
                      var entry: ArArchiveEntry = null
                      // not needed ??? supposed to allow to protect against zip bombs
                      var remainingSpace: Long = Long.MaxValue
                      while ({
                        entry = ais.getNextEntry
                        entry != null
                      })
                        if (
                          entry.getName.startsWith(path) &&
                          isSelected(entry.getName, fileInfo(entry))
                        ) {
                          val bis = new BoundedInputStream(ais, remainingSpace + 1)
                          val cis = new CountingInputStream(bis)
                          extractFile(
                            getSourceFile,
                            outputDirectory,
                            cis,
                            entry.getName,
                            entry.getLastModifiedDate,
                            entry.isDirectory,
                            Some(entry.getMode).filter(_ != 0).map(x => x: Integer).orNull,
                            null,
                            getFileMappers
                          )
                          remainingSpace -= cis.getByteCount
                          if (remainingSpace < 0)
                            throw new ArchiverException("Maximum output size limit reached")
                        }
                    }
                  catch {
                    case ex: IOException =>
                      throw new ArchiverException(
                        "Error while expanding " + getSourceFile.getAbsolutePath,
                        ex
                      )
                  }
              }
            Right(unArc)
          case ArchiveType.Tar =>
            Right(new TarUnArchiver)
          case ArchiveType.Tgz =>
            Right(new TarGZipUnArchiver)
          case ArchiveType.Tbz2 =>
            Right(new TarBZip2UnArchiver)
          case ArchiveType.Txz =>
            Right(new TarXZUnArchiver)
          case ArchiveType.Tzst =>
            Right(new TarZstdUnArchiver)
        }

      Files.createDirectories(destDir.toPath)

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

    def inputStream(archiveType: ArchiveType.Compressed, is: InputStream): InputStream =
      archiveType match {
        case ArchiveType.Gzip => new GZIPInputStream(is)
        case ArchiveType.Xz   => new XZCompressorInputStream(is)
      }
  }

  private final class SudoAbleUnArchiver(useSudo: Boolean) extends UnArchiver with OpenStream {
    def extract(
      archiveType: ArchiveType,
      archive: File,
      destDir: File,
      overwrite: Boolean
    ): Unit =
      archiveType match {
        case compressed: ArchiveType.Compressed =>
          // single file to decompress
          val proceed = new DefaultUnArchiver().extractCompressed(compressed, archive, destDir)
          Files.createDirectories(destDir.toPath)
          proceed()
        case tar: ArchiveType.Tar =>
          val (compressionOptionOneLetter, compressionArgs) = tar match {
            case ArchiveType.Tar  => ("", Nil)
            case ArchiveType.Tgz  => ("z", Nil)
            case ArchiveType.Tbz2 => ("j", Nil)
            case ArchiveType.Txz  => ("J", Nil)
            case ArchiveType.Tzst => ("", Seq("--zstd"))
          }
          val maybeSudo = if (useSudo) Seq("sudo") else Nil
          val command = maybeSudo ++ Seq("tar") ++ compressionArgs ++
            Seq("-" + compressionOptionOneLetter + "xf", archive.toString)
          Files.createDirectories(destDir.toPath)
          val proc = new ProcessBuilder()
            .command(command.asJava)
            .directory(destDir)
            .inheritIO()
            .start()
          val retCode = proc.waitFor()
          if (retCode != 0)
            sys.error(s"Error extracting $archive under $destDir (see tar command messages above)")
        case other =>
          new DefaultUnArchiver().extract(archiveType, archive, destDir, overwrite)
      }

    def inputStream(archiveType: ArchiveType.Compressed, is: InputStream): InputStream =
      new DefaultUnArchiver().inputStream(archiveType, is)
  }

  def default(): UnArchiver with OpenStream =
    new DefaultUnArchiver
  def priviledged(): UnArchiver =
    new SudoAbleUnArchiver(true)
  def priviledgedTestMode(): UnArchiver =
    new SudoAbleUnArchiver(false)
}
