package coursier.install.internal

import coursier.cache.ArchiveType
import org.apache.commons.compress.archivers.{ArchiveEntry, ArchiveStreamFactory}
import org.apache.commons.compress.compressors.CompressorStreamFactory

import java.io.{BufferedInputStream, File, InputStream}
import java.nio.file.Files
import java.util.zip.{GZIPInputStream, ZipEntry, ZipFile}

import scala.jdk.CollectionConverters._

object ArchiveUtil {

  private def withCompressedTarArchiveEntriesIterator[T](
    archive: File,
    compression: ArchiveType.Tar
  )(
    f: Iterator[(ArchiveEntry, InputStream)] => T
  ): T = {

    val method = compression match {
      case ArchiveType.Tgz  => CompressorStreamFactory.GZIP
      case ArchiveType.Tbz2 => CompressorStreamFactory.BZIP2
    }

    // https://alexwlchan.net/2019/09/unpacking-compressed-archives-in-scala/
    var fis: InputStream = null
    try {
      fis = Files.newInputStream(archive.toPath)
      val uncompressedInputStream = new CompressorStreamFactory()
        .createCompressorInputStream(
          method,
          if (fis.markSupported()) fis
          else new BufferedInputStream(fis)
        )
      val archiveInputStream = new ArchiveStreamFactory().createArchiveInputStream(
        if (uncompressedInputStream.markSupported()) uncompressedInputStream
        else new BufferedInputStream(uncompressedInputStream)
      )

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
            val value = (nextEntryOrNull, archiveInputStream)
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
    withCompressedTarArchiveEntriesIterator(archive, compression) { it =>
      val it0 = it.filter(!_._1.isDirectory).map(_._2)
      if (it0.hasNext)
        f(it0.next())
      else
        throw new NoSuchElementException(s"No file found in $archive")
    }

  def withFileInCompressedTarArchive[T](
    archive: File,
    compression: ArchiveType.Tar,
    pathInArchive: String
  )(f: InputStream => T): T =
    withCompressedTarArchiveEntriesIterator(archive, compression) { it =>
      val it0 = it.collect {
        case (ent, is) if !ent.isDirectory && ent.getName == pathInArchive =>
          is
      }
      if (it0.hasNext)
        f(it0.next())
      else
        throw new NoSuchElementException(s"$pathInArchive not found in $archive")
    }

  def withGzipContent[T](gzFile: File)(f: InputStream => T): T = {
    var fis: InputStream      = null
    var gzis: GZIPInputStream = null
    try {
      fis = Files.newInputStream(gzFile.toPath)
      gzis = new GZIPInputStream(fis)
      f(gzis)
    }
    finally {
      if (gzis != null) gzis.close()
      if (fis != null) fis.close()
    }
  }

  def withFirstFileInZip[T](zip: File)(f: InputStream => T): T = {
    var zf: ZipFile     = null
    var is: InputStream = null
    try {
      zf = new ZipFile(zip)
      val ent = zf.entries().asScala.find(e => !e.isDirectory).getOrElse {
        throw new NoSuchElementException(s"No file found in $zip")
      }
      is = zf.getInputStream(ent)
      f(is)
    }
    finally {
      if (zf != null)
        zf.close()
      if (is != null)
        is.close()
    }
  }

  def withFileInZip[T](zip: File, pathInArchive: String)(f: InputStream => T): T = {
    var zf: ZipFile     = null
    var is: InputStream = null
    try {
      zf = new ZipFile(zip)
      val ent = Option(zf.getEntry(pathInArchive)).getOrElse {
        throw new NoSuchElementException(s"$pathInArchive not found in $zip")
      }
      is = zf.getInputStream(ent)
      f(is)
    }
    finally {
      if (zf != null)
        zf.close()
      if (is != null)
        is.close()
    }
  }

}
