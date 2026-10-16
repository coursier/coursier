package coursier.cache

import org.apache.commons.compress.archivers.ar.{ArArchiveEntry, ArArchiveInputStream}
import org.apache.commons.compress.compressors.xz.XZCompressorInputStream
import org.apache.commons.io.input.{BoundedInputStream, CountingInputStream}
import org.codehaus.plexus.archiver.{AbstractUnArchiver, ArchiverException}
import org.codehaus.plexus.archiver.tar.{
  TarBZip2UnArchiver,
  TarGZipUnArchiver,
  TarXZUnArchiver,
  TarZstdUnArchiver,
  TarUnArchiver
}
import org.codehaus.plexus.archiver.zip.ZipUnArchiver
import org.codehaus.plexus.components.io.filemappers.FileMapper
import org.codehaus.plexus.components.io.resources.PlexusIoResource

import java.io.{BufferedInputStream, File, IOException, InputStream, OutputStream}
import java.nio.file.{Files, Path, Paths}
import java.util.Date
import java.util.zip.GZIPInputStream

import scala.collection.mutable
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
      lazy val linkChecker = new SymbolicLinkChecker(destDir.toPath)
      val unArchiver: Either[() => Unit, org.codehaus.plexus.archiver.UnArchiver] =
        archiveType match {
          case c: ArchiveType.Compressed =>
            Left(extractCompressed(c, archive, destDir))
          case ArchiveType.Zip =>
            Right(new ZipUnArchiver with CheckSymbolicLinks { def checker = linkChecker })
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
            Right(new TarUnArchiver with CheckSymbolicLinks { def checker = linkChecker })
          case ArchiveType.Tgz =>
            Right(new TarGZipUnArchiver with CheckSymbolicLinks { def checker = linkChecker })
          case ArchiveType.Tbz2 =>
            Right(new TarBZip2UnArchiver with CheckSymbolicLinks { def checker = linkChecker })
          case ArchiveType.Txz =>
            Right(new TarXZUnArchiver with CheckSymbolicLinks { def checker = linkChecker })
          case ArchiveType.Tzst =>
            Right(new TarZstdUnArchiver with CheckSymbolicLinks { def checker = linkChecker })
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
          linkChecker.checkAll()
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
          val command   = maybeSudo ++ Seq("tar") ++ compressionArgs ++
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

  /** Mixed into plexus unarchivers, to check the symbolic links they extract
    *
    * plexus-archiver checks that archive entries aren't written outside of the extraction
    * directory, but it creates symbolic links pointing anywhere, like `foo -> /home/user`.
    */
  private trait CheckSymbolicLinks extends AbstractUnArchiver {
    def checker: SymbolicLinkChecker
    override protected def extractFile(
      srcF: File,
      dir: File,
      compressedInputStream: InputStream,
      entryName: String,
      entryDate: Date,
      isDirectory: Boolean,
      mode: Integer,
      symlinkDestination: String,
      fileMappers: Array[FileMapper]
    ): Unit = {
      if (symlinkDestination != null && symlinkDestination.nonEmpty) {
        val mappedEntryName = Option(fileMappers).getOrElse(Array.empty[FileMapper])
          .foldLeft(entryName)((name, mapper) => mapper.getMappedFileName(name))
        checker.checkEntry(mappedEntryName, Paths.get(symlinkDestination))
      }
      super.extractFile(
        srcF,
        dir,
        compressedInputStream,
        entryName,
        entryDate,
        isDirectory,
        mode,
        symlinkDestination,
        fileMappers
      )
    }
  }

  /** Checks that symbolic links extracted under `root` don't point outside of it
    *
    * Each link is checked before it's created, against what has been extracted so far. But a link
    * accepted at that point can end up outside of `root` because of a link extracted later, like
    * `sub/b -> c/../foo` followed by `sub/c -> ..`, so `checkAll` checks them all again once the
    * extraction is done, without having to walk the extracted files.
    *
    * Link targets are resolved by asking the file system whether each of their components is a
    * symbolic link, rather than by keeping track of the extracted entries, so that names are
    * matched the way the file system matches them (case-insensitive ones, Windows short names…)
    */
  private final class SymbolicLinkChecker(root0: Path) {

    private val root = root0.toAbsolutePath.normalize

    // Maximum number of symbolic links followed when resolving a target, like Linux' MAXSYMLINKS
    private def maxLinks = 40

    // Links extracted so far, as entry paths relative to root
    private val links = new mutable.LinkedHashSet[Path]

    private def fail(entry: Path, target: Path, reason: String): Nothing =
      throw new ArchiverException(s"Symbolic link $entry -> $target $reason, in $root")

    // Resolves `target` relative to `dir`, both under `root`, and returns the number of symbolic
    // links followed so far, or None if it ends up outside of `root`
    private def resolve(
      entry: Path,
      dir: Path,
      target: Path,
      linkCount: Int
    ): Option[(Path, Int)] =
      if (target.isAbsolute || target.getRoot != null)
        fail(entry, target, "has an absolute target")
      else
        target.iterator().asScala.map(_.toString).foldLeft(Option((dir, linkCount))) {
          case (None, _)                                     => None
          case (Some((current, _)), "..") if current == root =>
            None
          case (Some((current, count)), "..") =>
            Some((current.getParent, count))
          case (acc @ Some(_), "" | ".") =>
            acc
          case (Some((current, count)), name) =>
            val next = current.resolve(name)
            if (Files.isSymbolicLink(next))
              if (count >= maxLinks)
                fail(entry, target, "goes through too many symbolic links")
              else
                resolve(entry, current, Files.readSymbolicLink(next), count + 1)
            else
              Some((next, count))
        }

    // `entry` is relative to root
    private def checkLink(entry: Path, target: Path): Unit = {
      // where the directory of entry actually is, following symbolic links
      val dirOpt   = resolve(entry, root, Option(entry.getParent).getOrElse(Paths.get("")), 0)
      val isInside = dirOpt.exists {
        case (dir, _) =>
          resolve(entry, dir, target, 0).nonEmpty
      }
      if (!isInside)
        fail(entry, target, "points outside of the extraction directory")
    }

    /** Checks a link entry, before it's extracted */
    def checkEntry(entryName: String, target: Path): Unit = {
      val entry = root.relativize(root.resolve(entryName).normalize)
      // plexus-archiver rejects entries outside of root itself
      if (!entry.startsWith("..")) {
        checkLink(entry, target)
        links += entry
      }
    }

    /** Checks again all the links extracted so far */
    def checkAll(): Unit =
      for (entry <- links) {
        val link = root.resolve(entry)
        // plexus-archiver doesn't create links if something already exists at their path
        if (Files.isSymbolicLink(link))
          checkLink(entry, Files.readSymbolicLink(link))
      }
  }

  def default(): UnArchiver with OpenStream =
    new DefaultUnArchiver
  def priviledged(): UnArchiver =
    new SudoAbleUnArchiver(true)
  def priviledgedTestMode(): UnArchiver =
    new SudoAbleUnArchiver(false)
}
