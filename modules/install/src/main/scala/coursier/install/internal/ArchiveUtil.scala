package coursier.install.internal

import coursier.cache.{ArchiveType, UnArchiver}
import org.apache.commons.compress.archivers.{
  ArchiveEntry,
  ArchiveInputStream,
  ArchiveStreamFactory
}
import org.apache.commons.compress.compressors.CompressorStreamFactory
import org.apache.commons.compress.compressors.xz.XZCompressorInputStream

import java.io.{BufferedInputStream, File, InputStream}
import java.nio.file.Files
import java.util.zip.{GZIPInputStream, ZipEntry, ZipFile}

import scala.jdk.CollectionConverters._

object ArchiveUtil {

  // Content read from archives is written to disk by callers, so it's subject to the same size
  // limit as archives extracted by ArchiveCache (UnArchiver.defaultMaxSize, unless maxSize is
  // passed explicitly)
  private def limitSize(archive: File, is: InputStream, maxSize: Long): InputStream =
    UnArchiver.limitExtractedSize(archive, is, maxSize)

  private def withCompressedTarArchiveEntriesIterator[T](
    archive: File,
    compression: ArchiveType.Tar
  )(
    f: Iterator[(ArchiveEntry, InputStream)] => T
  ): T = {

    val methodOpt = compression match {
      case ArchiveType.Tar  => None
      case ArchiveType.Tgz  => Some(CompressorStreamFactory.GZIP)
      case ArchiveType.Tbz2 => Some(CompressorStreamFactory.BZIP2)
      case ArchiveType.Txz  => Some(CompressorStreamFactory.XZ)
      case ArchiveType.Tzst => Some(CompressorStreamFactory.ZSTANDARD)
    }

    // https://alexwlchan.net/2019/09/unpacking-compressed-archives-in-scala/
    var fis: InputStream = null
    try {
      fis = Files.newInputStream(archive.toPath)
      val uncompressedInputStream = methodOpt match {
        case Some(method) =>
          new CompressorStreamFactory().createCompressorInputStream(
            method,
            if (fis.markSupported()) fis
            else new BufferedInputStream(fis)
          )
        case None =>
          fis
      }
      val archiveInputStream: ArchiveInputStream[_ <: ArchiveEntry] =
        new ArchiveStreamFactory().createArchiveInputStream(
          if (uncompressedInputStream.markSupported()) uncompressedInputStream
          else new BufferedInputStream(uncompressedInputStream)
        )

      // Entries before the wanted one are skipped, not written anywhere: archives with lots of
      // them only cost CPU time
      var nextEntryOrNull: ArchiveEntry = null

      val it: Iterator[(ArchiveEntry, InputStream)] =
        new Iterator[(ArchiveEntry, InputStream)] {
          def hasNext: Boolean = {
            if (nextEntryOrNull == null)
              nextEntryOrNull = archiveInputStream.getNextEntry
            nextEntryOrNull != null
          }
          def next(): (ArchiveEntry, InputStream) = {
            assert(hasNext)
            val value: (ArchiveEntry, InputStream) = (nextEntryOrNull, archiveInputStream)
            nextEntryOrNull = null
            value
          }
        }

      f(it)
    }
    finally if (fis != null)
        fis.close()
  }

  def withFirstFileInCompressedTarArchive[T](
    archive: File,
    compression: ArchiveType.Tar
  )(f: InputStream => T): T =
    withFirstFileInCompressedTarArchive(archive, compression, UnArchiver.defaultMaxSize)(f)

  def withFirstFileInCompressedTarArchive[T](
    archive: File,
    compression: ArchiveType.Tar,
    maxSize: Long
  )(f: InputStream => T): T =
    withCompressedTarArchiveEntriesIterator(archive, compression) { it =>
      val it0 = it.filter(!_._1.isDirectory).map(_._2)
      if (it0.hasNext)
        f(limitSize(archive, it0.next(), maxSize))
      else
        throw new NoSuchElementException(s"No file found in $archive")
    }

  def withFileInCompressedTarArchive[T](
    archive: File,
    compression: ArchiveType.Tar,
    pathInArchive: String
  )(f: InputStream => T): T =
    withFileInCompressedTarArchive(archive, compression, pathInArchive, UnArchiver.defaultMaxSize)(
      f
    )

  def withFileInCompressedTarArchive[T](
    archive: File,
    compression: ArchiveType.Tar,
    pathInArchive: String,
    maxSize: Long
  )(f: InputStream => T): T =
    withCompressedTarArchiveEntriesIterator(archive, compression) { it =>
      val it0 = it.collect {
        case (ent, is) if !ent.isDirectory && ent.getName == pathInArchive =>
          is
      }
      if (it0.hasNext)
        f(limitSize(archive, it0.next(), maxSize))
      else
        throw new NoSuchElementException(s"$pathInArchive not found in $archive")
    }

  def withGzipContent[T](gzFile: File)(f: InputStream => T): T =
    withGzipContent(gzFile, UnArchiver.defaultMaxSize)(f)

  def withGzipContent[T](gzFile: File, maxSize: Long)(f: InputStream => T): T =
    withCompressedContent(gzFile, ArchiveType.Gzip, maxSize)(f)

  def withCompressedContent[T](
    file: File,
    archiveType: ArchiveType.Compressed
  )(f: InputStream => T): T =
    withCompressedContent(file, archiveType, UnArchiver.defaultMaxSize)(f)

  def withCompressedContent[T](
    file: File,
    archiveType: ArchiveType.Compressed,
    maxSize: Long
  )(f: InputStream => T): T = {
    var fis: InputStream = null
    var is: InputStream  = null
    try {
      fis = Files.newInputStream(file.toPath)
      is = archiveType match {
        case ArchiveType.Gzip => new GZIPInputStream(fis, 64 * 1024)
        case ArchiveType.Xz => new XZCompressorInputStream(new BufferedInputStream(fis, 64 * 1024))
      }
      f(limitSize(file, is, maxSize))
    }
    finally {
      if (is != null) is.close()
      if (fis != null) fis.close()
    }
  }

  def withFirstFileInZip[T](zip: File)(f: InputStream => T): T =
    withFirstFileInZip(zip, UnArchiver.defaultMaxSize)(f)

  def withFirstFileInZip[T](zip: File, maxSize: Long)(f: InputStream => T): T = {
    var zf: ZipFile     = null
    var is: InputStream = null
    try {
      zf = new ZipFile(zip)
      val ent = zf.entries().asScala.find(e => !e.isDirectory).getOrElse {
        throw new NoSuchElementException(s"No file found in $zip")
      }
      is = zf.getInputStream(ent)
      f(limitSize(zip, is, maxSize))
    }
    finally {
      if (zf != null)
        zf.close()
      if (is != null)
        is.close()
    }
  }

  def withFileInZip[T](zip: File, pathInArchive: String)(f: InputStream => T): T =
    withFileInZip(zip, pathInArchive, UnArchiver.defaultMaxSize)(f)

  def withFileInZip[T](zip: File, pathInArchive: String, maxSize: Long)(
    f: InputStream => T
  ): T = {
    var zf: ZipFile     = null
    var is: InputStream = null
    try {
      zf = new ZipFile(zip)
      val ent = Option(zf.getEntry(pathInArchive)).getOrElse {
        throw new NoSuchElementException(s"$pathInArchive not found in $zip")
      }
      is = zf.getInputStream(ent)
      f(limitSize(zip, is, maxSize))
    }
    finally {
      if (zf != null)
        zf.close()
      if (is != null)
        is.close()
    }
  }

}
