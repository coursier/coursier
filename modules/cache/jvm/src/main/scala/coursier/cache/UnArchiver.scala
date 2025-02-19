package coursier.cache

import com.sprylab.xar.{FileXarSource, XarEntry}
import org.apache.commons.compress.archivers.ar.{ArArchiveEntry, ArArchiveInputStream}
import org.apache.commons.compress.archivers.cpio.{CpioArchiveEntry, CpioArchiveInputStream}
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream
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

import java.io.{
  ByteArrayInputStream,
  BufferedInputStream,
  File,
  IOException,
  InputStream,
  OutputStream
}
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
          case ArchiveType.Xar =>
            val unArc: org.codehaus.plexus.archiver.UnArchiver =
              new org.codehaus.plexus.archiver.AbstractUnArchiver {
                def fileInfo(entry: XarEntry): PlexusIoResource =
                  new PlexusIoResource {
                    def getName         = entry.getName
                    def isSymbolicLink  = false
                    def getContents     = ???
                    def getLastModified = entry.getTime.toInstant.getEpochSecond
                    def getSize         = entry.getSize
                    def getURL          = null
                    def isDirectory     = entry.isDirectory
                    def isExisting      = true
                    def isFile          = !isDirectory
                  }
                def execute(): Unit = execute("", getDestDirectory)
                // based on org.codehaus.plexus.archiver.zip.AbstractZipUnArchiver
                def execute(path: String, outputDirectory: File): Unit =
                  try {
                    val source = new FileXarSource(getSourceFile)
                    val it     = source.getEntries.asScala.iterator

                    // not needed ??? supposed to allow to protect against zip bombs
                    var remainingSpace: Long = Long.MaxValue
                    for {
                      entry <- it
                      if entry.getName.startsWith(path) &&
                      isSelected(entry.getName, fileInfo(entry))
                    } {
                      if (entry.isDirectory)
                        extractFile(
                          getSourceFile,
                          outputDirectory,
                          new ByteArrayInputStream(Array.emptyByteArray),
                          entry.getName,
                          entry.getTime,
                          entry.isDirectory,
                          Integer.decode(entry.getMode),
                          null,
                          getFileMappers
                        )
                      else {
                        val originalEncoding = entry.getEncoding
                        if (originalEncoding == com.sprylab.xar.toc.model.Encoding.BZIP2) {
                          val fld = entry.getClass.getDeclaredField("encoding")
                          fld.setAccessible(true)
                          fld.set(entry, com.sprylab.xar.toc.model.Encoding.NONE)
                        }
                        Using.resource(entry.getInputStream()) { eis =>
                          val bis = new BoundedInputStream(eis, remainingSpace + 1)
                          val cis = new CountingInputStream(bis)
                          val is = originalEncoding match {
                            case com.sprylab.xar.toc.model.Encoding.NONE => cis
                            case com.sprylab.xar.toc.model.Encoding.GZIP =>
                              // new GZIPInputStream(cis)
                              cis
                            case com.sprylab.xar.toc.model.Encoding.BZIP2 =>
                              new BZip2CompressorInputStream(cis)
                          }
                          extractFile(
                            getSourceFile,
                            outputDirectory,
                            is,
                            entry.getName,
                            entry.getTime,
                            entry.isDirectory,
                            Integer.decode(entry.getMode),
                            null,
                            getFileMappers
                          )
                          remainingSpace -= cis.getByteCount
                        }
                      }

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
          case ArchiveType.Cpio(compression) =>
            val unArc: org.codehaus.plexus.archiver.UnArchiver =
              new org.codehaus.plexus.archiver.AbstractUnArchiver {
                val mask = Integer.decode("0777")
                def fileInfo(entry: CpioArchiveEntry): PlexusIoResource =
                  new PlexusIoResource {
                    def getName         = entry.getName
                    def isSymbolicLink  = false
                    def getContents     = ???
                    def getLastModified = entry.getTime
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
                      val is = compression match {
                        case Some(ArchiveType.Gzip) => new GZIPInputStream(fis)
                        case Some(ArchiveType.Xz)   => new XZCompressorInputStream(fis)
                        case None                   => fis
                      }
                      val ais = new CpioArchiveInputStream(new BufferedInputStream(is))
                      var entry: CpioArchiveEntry = null
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
                            (entry.getMode & mask).toInt,
                            null,
                            getFileMappers
                          )
                          import org.apache.commons.compress.archivers.cpio.CpioConstants
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
