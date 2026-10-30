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

import java.io.{
  BufferedInputStream,
  File,
  FilterInputStream,
  IOException,
  InputStream,
  OutputStream
}
import java.nio.file.{Files, Path, Paths}
import java.util.Date
import java.util.zip.GZIPInputStream

import scala.collection.mutable
import scala.jdk.CollectionConverters._
import scala.util.{Try, Using}

import coursier.util.EnvEntry

trait UnArchiver {
  def extract(archiveType: ArchiveType, archive: File, destDir: File, overwrite: Boolean): Unit
}

object UnArchiver {
  trait OpenStream {
    def inputStream(archiveType: ArchiveType.Compressed, is: InputStream): InputStream
  }

  private final class DefaultUnArchiver(maxSize: Long, maxEntries: Long) extends UnArchiver
      with OpenStream {

    // for binary compatibility
    def this() = this(defaultMaxSize, defaultMaxEntries)

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
              val limits = new ExtractionLimits(archive, maxSize, maxEntries)
              limits.newEntry()
              fis = Files.newInputStream(archive.toPath)
              gzis = new GZIPInputStream(fis)
              val is = limits.limit(gzis)
              fos = Files.newOutputStream(dest.toPath)

              val buf  = Array.ofDim[Byte](16 * 1024)
              var read = -1
              while ({
                read = is.read(buf)
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
              val limits = new ExtractionLimits(archive, maxSize, maxEntries)
              limits.newEntry()
              fis = Files.newInputStream(archive.toPath)
              xis = new XZCompressorInputStream(fis)
              val is = limits.limit(xis)
              fos = Files.newOutputStream(dest.toPath)

              val buf  = Array.ofDim[Byte](16 * 1024)
              var read = -1
              while ({
                read = is.read(buf)
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
      lazy val limits0     = new ExtractionLimits(archive, maxSize, maxEntries)
      val unArchiver: Either[() => Unit, org.codehaus.plexus.archiver.UnArchiver] =
        archiveType match {
          case c: ArchiveType.Compressed =>
            Left(extractCompressed(c, archive, destDir))
          case ArchiveType.Zip =>
            Right {
              new ZipUnArchiver with CheckSymbolicLinks with LimitExtraction {
                def checker = linkChecker
                def limits  = limits0
              }
            }
          case ArchiveType.Ar =>
            val unArc: org.codehaus.plexus.archiver.UnArchiver =
              new org.codehaus.plexus.archiver.AbstractUnArchiver with LimitExtraction {
                def limits                                            = limits0
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
            Right {
              new TarUnArchiver with CheckSymbolicLinks with LimitExtraction {
                def checker = linkChecker
                def limits  = limits0
              }
            }
          case ArchiveType.Tgz =>
            Right {
              new TarGZipUnArchiver with CheckSymbolicLinks with LimitExtraction {
                def checker = linkChecker
                def limits  = limits0
              }
            }
          case ArchiveType.Tbz2 =>
            Right {
              new TarBZip2UnArchiver with CheckSymbolicLinks with LimitExtraction {
                def checker = linkChecker
                def limits  = limits0
              }
            }
          case ArchiveType.Txz =>
            Right {
              new TarXZUnArchiver with CheckSymbolicLinks with LimitExtraction {
                def checker = linkChecker
                def limits  = limits0
              }
            }
          case ArchiveType.Tzst =>
            Right {
              new TarZstdUnArchiver with CheckSymbolicLinks with LimitExtraction {
                def checker = linkChecker
                def limits  = limits0
              }
            }
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

  /** Extracts archives with plexus-archiver and commons-compress, apart from tar archives, that are
    * extracted by running the `tar` command (with sudo if `useSudo` is true)
    *
    * The extraction limits of [[default]] don't apply to archives extracted with `tar`.
    */
  private final class SudoAbleUnArchiver(useSudo: Boolean) extends UnArchiver with OpenStream {
    private def defaultUnArchiver(): DefaultUnArchiver =
      new DefaultUnArchiver(defaultMaxSize, defaultMaxEntries)

    def extract(
      archiveType: ArchiveType,
      archive: File,
      destDir: File,
      overwrite: Boolean
    ): Unit =
      archiveType match {
        case compressed: ArchiveType.Compressed =>
          // single file to decompress
          val proceed = defaultUnArchiver().extractCompressed(compressed, archive, destDir)
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
          defaultUnArchiver().extract(archiveType, archive, destDir, overwrite)
      }

    def inputStream(archiveType: ArchiveType.Compressed, is: InputStream): InputStream =
      defaultUnArchiver().inputStream(archiveType, is)
  }

  /** Limits on the total size and number of entries written when extracting an archive
    *
    * Guards against archive bombs, small archives that expand to huge sizes or numbers of files.
    * Limits that are zero or negative are ignored.
    */
  private final class ExtractionLimits(archive: File, maxSize: Long, maxEntries: Long) {
    private var size    = 0L
    private var entries = 0L

    private def fail(what: String, limit: Long, entry: EnvEntry): Nothing =
      throw new ExtractionLimitException(
        s"Extracting $archive exceeds the maximum $what ($limit). " +
          s"Set ${entry.envName} in the environment, or the ${entry.propName} Java property, " +
          "to change that limit (0 to disable it)."
      )

    /** To be called before each entry is extracted */
    def newEntry(): Unit = {
      entries += 1
      if (maxEntries > 0 && entries > maxEntries)
        fail("number of entries", maxEntries, maxEntriesEntry)
    }

    /** Wraps a stream of extracted content, so that it fails if the size limit is exceeded */
    def limit(is: InputStream): InputStream =
      new FilterInputStream(is) {
        private def count(read: Long): Unit =
          if (read > 0) {
            size += read
            if (maxSize > 0 && size > maxSize)
              fail("extracted size", maxSize, maxSizeEntry)
          }
        override def read(): Int = {
          val b = super.read()
          if (b >= 0) count(1L)
          b
        }
        override def read(b: Array[Byte], off: Int, len: Int): Int = {
          val read = super.read(b, off, len)
          count(read)
          read
        }
      }
  }

  /** Thrown when extracting an archive exceeds the limits of [[UnArchiver.default]] */
  final class ExtractionLimitException(message: String) extends ArchiverException(message)

  /** Mixed into plexus unarchivers, to enforce extraction limits */
  private trait LimitExtraction extends AbstractUnArchiver {
    def limits: ExtractionLimits
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
      limits.newEntry()
      super.extractFile(
        srcF,
        dir,
        if (compressedInputStream == null) null else limits.limit(compressedInputStream),
        entryName,
        entryDate,
        isDirectory,
        mode,
        symlinkDestination,
        fileMappers
      )
    }
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

  private def maxSizeEntry =
    EnvEntry("COURSIER_ARCHIVE_MAX_SIZE", "coursier.archive.max-size")
  private def maxEntriesEntry =
    EnvEntry("COURSIER_ARCHIVE_MAX_ENTRIES", "coursier.archive.max-entries")

  /** Default maximum size of the content extracted from an archive, in bytes (8 GiB)
    *
    * JDK or GraalVM archives are several hundreds of MB once extracted, so that leaves a lot of
    * room, while preventing archive bombs from filling the disk.
    */
  def defaultMaxSizeFallback: Long = 8L * 1024L * 1024L * 1024L

  /** Default maximum number of entries extracted from an archive */
  def defaultMaxEntriesFallback: Long = 1000000L

  // accepts a number of bytes, optionally followed by a K, M, G, or T suffix (powers of 1024)
  private def parseSize(input: String): Option[Long] = {
    val s             = input.trim
    val (num, factor) = s.lastOption.map(_.toUpper) match {
      case Some('K') => (s.init, 1024L)
      case Some('M') => (s.init, 1024L * 1024L)
      case Some('G') => (s.init, 1024L * 1024L * 1024L)
      case Some('T') => (s.init, 1024L * 1024L * 1024L * 1024L)
      case _         => (s, 1L)
    }
    Try(Math.multiplyExact(num.trim.toLong, factor)).toOption
  }

  private def readLimit(entry: EnvEntry, fallback: Long): Long = {
    val values = entry.read()
    values.env.map(v => (v, s"environment variable ${entry.envName}"))
      .orElse(values.prop.map(v => (v, s"Java property ${entry.propName}")))
      .fold(fallback) {
        case (value, from) =>
          parseSize(value).getOrElse {
            throw new IllegalArgumentException(s"Invalid value '$value' for $from")
          }
      }
  }

  /** Maximum size of the content extracted from an archive by [[default]], in bytes
    *
    * Can be set via the `COURSIER_ARCHIVE_MAX_SIZE` environment variable or the
    * `coursier.archive.max-size` Java property, as a number of bytes, optionally followed by K, M,
    * G, or T (like `32G`). 0 disables the limit.
    */
  def defaultMaxSize: Long =
    readLimit(maxSizeEntry, defaultMaxSizeFallback)

  /** Maximum number of entries extracted from an archive by [[default]]
    *
    * Can be set via the `COURSIER_ARCHIVE_MAX_ENTRIES` environment variable or the
    * `coursier.archive.max-entries` Java property. 0 disables the limit.
    */
  def defaultMaxEntries: Long =
    readLimit(maxEntriesEntry, defaultMaxEntriesFallback)

  def default(): UnArchiver with OpenStream =
    new DefaultUnArchiver(defaultMaxSize, defaultMaxEntries)

  /** An [[UnArchiver]] failing with [[ExtractionLimitException]] if an archive has more than
    * `maxEntries` entries, or more than `maxSize` bytes of content once extracted
    *
    * Zero or negative values disable the corresponding limit.
    */
  def default(maxSize: Long, maxEntries: Long): UnArchiver with OpenStream =
    new DefaultUnArchiver(maxSize, maxEntries)
  def priviledged(): UnArchiver =
    new SudoAbleUnArchiver(true)
  def priviledgedTestMode(): UnArchiver =
    new SudoAbleUnArchiver(false)
}
