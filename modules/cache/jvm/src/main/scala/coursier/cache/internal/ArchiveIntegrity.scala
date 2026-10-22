package coursier.cache.internal

import java.io.{EOFException, File, InputStream}
import java.nio.file.Files
import java.util.zip.{GZIPInputStream, ZipException, ZipFile}

import scala.jdk.CollectionConverters._

import coursier.cache.{ArtifactError, CacheLocks, FileCache}
import coursier.util.{EitherT, Sync}

/** Integrity checks of archives, using the checksums of their compression format
  *
  * See `fileWithIntegrityCheck` in [[coursier.cache.FileCache]] and its parent classes.
  */
private[coursier] object ArchiveIntegrity {

  private def isGzip(url: String): Boolean =
    url.endsWith(".gz") || url.endsWith(".tgz") || url.endsWith(".apk")
  private def isZip(url: String): Boolean =
    url.endsWith(".zip") || url.endsWith(".jar")

  /** Checks the integrity of an archive
    *
    * Only supported for GZIP and ZIP archives for now.
    *
    * @return
    *   whether the archive is valid, or `None` if its format isn't supported
    */
  def check(url: String, file: File): Option[Boolean] = {
    def readAndDiscard(is: InputStream): Unit = {
      val buf  = Array.ofDim[Byte](64 * 1024)
      var read = 0
      while ({
        read = is.read(buf)
        read >= 0
      }) {}
    }
    if (isGzip(url)) {
      // read uncompressed content fully to validate GZIP checksum
      var fis: InputStream = null
      try {
        fis = Files.newInputStream(file.toPath)
        try {
          readAndDiscard(new GZIPInputStream(fis))
          Some(true)
        }
        catch {
          case _: ZipException =>
            Some(false)
          case ex: EOFException if ex.getMessage.contains("ZLIB") =>
            Some(false)
        }
      }
      finally
        if (fis != null)
          fis.close()
    }
    else if (isZip(url)) {
      // read all uncompressed entries fully to validate entries' checksums
      var zf: ZipFile = null
      try {
        zf = new ZipFile(file)
        for (ent <- zf.entries().asScala)
          readAndDiscard(zf.getInputStream(ent))
        Some(true)
      }
      catch {
        case _: ZipException =>
          Some(false)
      }
      finally
        if (zf != null)
          zf.close()
    }
    else
      None
  }

  private def isInCache(file: File, cacheLocation: File): Boolean =
    file.toPath.toAbsolutePath.normalize.startsWith(cacheLocation.toPath.toAbsolutePath.normalize)

  /** Checks the integrity of an archive in a cache, deleting it if it's corrupted
    *
    * An integrity file is written next to valid archives, so that they're not checked again.
    *
    * @return
    *   whether the archive is corrupted
    */
  private def checkCached(cacheLocation: File, url: String, file: File): Boolean =
    CacheLocks.withLockOr(cacheLocation, file)(
      {
        val integrityFile         = FileCache.auxiliaryFile(file, "integrity").toPath
        val hasValidIntegrityFile = Files.exists(integrityFile) && {
          val lastModified          = Files.getLastModifiedTime(file.toPath)
          val integrityLastModified = Files.getLastModifiedTime(integrityFile)
          integrityLastModified.toMillis() >= lastModified.toMillis()
        }
        if (!hasValidIntegrityFile)
          Files.deleteIfExists(integrityFile)
        !hasValidIntegrityFile && {
          val invalid = check(url, file).contains(false)
          if (invalid) {
            Files.delete(file.toPath)
            // clear all but the lock file for Windows?
            FileCache.clearAuxiliaryFiles(file)
          }
          else
            Files.write(integrityFile, Array.emptyByteArray)
          invalid
        }
      },
      None
    )

  /** Downloads an archive to a cache, and downloads it again if it's corrupted
    *
    * The integrity of the archive isn't checked after the second download, to avoid going into
    * loops.
    */
  def checkedDownload[F[_]](
    cacheLocation: File,
    url: String,
    download: => F[Either[ArtifactError, File]]
  )(implicit S: Sync[F]): F[Either[ArtifactError, File]] = {
    val eitherT = for {
      f       <- EitherT(download)
      invalid <- EitherT(
        S.delay[Either[ArtifactError, Boolean]] {
          Right {
            // Local file used in place (file: URL not copied to the cache). We mustn't write
            // integrity files next to it or delete it, and "re-downloading" it wouldn't change
            // anything anyway.
            isInCache(f, cacheLocation) && checkCached(cacheLocation, url, f)
          }
        }
      )
      f0 <- EitherT[F, ArtifactError, File](
        if (invalid) download
        else S.point(Right(f))
      )
    } yield f0
    eitherT.run
  }
}
