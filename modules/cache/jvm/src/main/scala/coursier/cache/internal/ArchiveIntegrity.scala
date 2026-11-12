package coursier.cache.internal

import java.io.{EOFException, File, InputStream}
import java.math.BigInteger
import java.nio.file.{Files, NoSuchFileException}
import java.security.{MessageDigest, NoSuchAlgorithmException}
import java.util.zip.{GZIPInputStream, ZipException, ZipFile}

import scala.jdk.CollectionConverters._

import coursier.cache.{ArtifactError, CacheLocks, ExpectedChecksum, FileCache}
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
          readAndDiscard(new GZIPInputStream(fis, 64 * 1024))
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

  /** Hex-encoded digest of `file`, computed without persisting it anywhere */
  def computeDigest(file: File, algorithm: String): BigInteger = {
    val md              = MessageDigest.getInstance(algorithm)
    var is: InputStream = null
    try {
      is = Files.newInputStream(file.toPath)
      val buf  = Array.ofDim[Byte](64 * 1024)
      var read = is.read(buf)
      while (read >= 0) {
        if (read > 0)
          md.update(buf, 0, read)
        read = is.read(buf)
      }
    }
    finally
      if (is != null)
        is.close()
    new BigInteger(1, md.digest())
  }

  /** Digest of `file`, read from the auxiliary file [[FileCache]] persists it to if there's one,
    * computed else
    *
    * Unlike [[FileCache]], this never writes anything, so that it can be used on caches managed by
    * others, like the one of a cache server.
    */
  def readOnlyDigest(file: File, algorithm: String): BigInteger = {
    val persisted = FileCache.auxiliaryFile(file, algorithm + ".computed").toPath
    val persistedOpt =
      try Some(Files.readAllBytes(persisted))
      catch {
        case _: NoSuchFileException => None
      }
    persistedOpt match {
      case Some(bytes) => new BigInteger(1, bytes)
      case None        => computeDigest(file, algorithm)
    }
  }

  /** Hex-encoded `digest`, padded to the length of the digests of `algorithm` */
  private def hex(digest: BigInteger, algorithm: String): String = {
    val hex0 = digest.toString(16)
    val len  = MessageDigest.getInstance(algorithm).getDigestLength * 2
    "0" * (len - hex0.length) + hex0
  }

  /** Checks the digest of `file` against `expected`
    *
    * @param digest
    *   computes the digest of a file, given a `MessageDigest` algorithm name
    */
  def checkChecksum(
    file: File,
    expected: ExpectedChecksum,
    digest: (File, String) => BigInteger
  ): Either[ArtifactError, Unit] = {
    val actualOpt =
      try Right(digest(file, expected.algorithm))
      catch {
        case e: NoSuchAlgorithmException =>
          Left(new ArtifactError.DownloadError(
            s"Unsupported checksum algorithm ${expected.algorithm} for $file",
            Some(e)
          ))
      }
    actualOpt.flatMap { actual =>
      val actualHex = hex(actual, expected.algorithm)
      if (actualHex == expected.normalizedValue) Right(())
      else
        Left(new ArtifactError.UnexpectedChecksum(
          expected.algorithm,
          actualHex,
          expected.normalizedValue,
          file.getAbsolutePath
        ))
    }
  }

  /** Checks an archive in a cache, deleting it if it's corrupted or has an unexpected checksum
    *
    * An integrity file is written next to archives whose integrity was checked, so that they're not
    * checked again, and the digest of the archive is persisted alongside it by `digest`.
    *
    * @return
    *   whether the archive was deleted, and should be downloaded again
    */
  private def checkCached(
    cacheLocation: File,
    url: String,
    file: File,
    integrityCheck: Boolean,
    expectedChecksum: Option[ExpectedChecksum],
    digest: (File, String) => BigInteger
  ): Either[ArtifactError, Boolean] =
    CacheLocks.withLockOr(cacheLocation, file)(
      {
        def delete(): Unit = {
          Files.delete(file.toPath)
          // clear all but the lock file for Windows?
          FileCache.clearAuxiliaryFiles(file)
        }
        val corrupted = integrityCheck && {
          val integrityFile = FileCache.auxiliaryFile(file, "integrity").toPath
          val hasValidIntegrityFile = Files.exists(integrityFile) && {
            val lastModified          = Files.getLastModifiedTime(file.toPath)
            val integrityLastModified = Files.getLastModifiedTime(integrityFile)
            integrityLastModified.toMillis() >= lastModified.toMillis()
          }
          if (!hasValidIntegrityFile)
            Files.deleteIfExists(integrityFile)
          !hasValidIntegrityFile && {
            val invalid = check(url, file).contains(false)
            if (invalid)
              delete()
            else
              Files.write(integrityFile, Array.emptyByteArray)
            invalid
          }
        }
        if (corrupted) Right(true)
        else
          expectedChecksum.map(checkChecksum(file, _, digest)) match {
            case Some(Left(_: ArtifactError.UnexpectedChecksum)) =>
              delete()
              Right(true)
            case Some(Left(err)) => Left(err)
            case _               => Right(false)
          }
      },
      None
    )

  private def checkChecksumCached(
    cacheLocation: File,
    file: File,
    expected: ExpectedChecksum,
    digest: (File, String) => BigInteger
  ): Either[ArtifactError, Unit] =
    CacheLocks.withLockOr(cacheLocation, file)(checkChecksum(file, expected, digest), None)

  /** Downloads an archive to a cache, and downloads it again if it's corrupted, or if its checksum
    * isn't the expected one
    *
    * The integrity of the archive isn't checked after the second download, to avoid going into
    * loops. Its checksum is, and an [[ArtifactError.UnexpectedChecksum]] is returned if it's still
    * not the expected one.
    *
    * Files outside of the cache (`file:` URLs not copied to the cache) are never deleted nor
    * "downloaded again", and nothing is written next to them. Only their checksum is checked.
    *
    * @param digest
    *   computes the digest of a file in the cache, given a `MessageDigest` algorithm name,
    *   persisting it if it wants to
    */
  def checkedDownload[F[_]](
    cacheLocation: File,
    url: String,
    integrityCheck: Boolean,
    expectedChecksum: Option[ExpectedChecksum],
    digest: (File, String) => BigInteger,
    download: => F[Either[ArtifactError, File]]
  )(implicit S: Sync[F]): F[Either[ArtifactError, File]] = {
    val eitherT = for {
      f <- EitherT(download)
      deleted <- EitherT(
        S.delay[Either[ArtifactError, Boolean]] {
          if (isInCache(f, cacheLocation))
            checkCached(cacheLocation, url, f, integrityCheck, expectedChecksum, digest)
          else
            expectedChecksum
              .map(checkChecksum(f, _, computeDigest))
              .getOrElse(Right(()))
              .map(_ => false)
        }
      )
      f0 <-
        if (deleted)
          EitherT(download).flatMap { f0 =>
            EitherT(
              S.delay[Either[ArtifactError, File]] {
                expectedChecksum
                  .map(checkChecksumCached(cacheLocation, f0, _, digest))
                  .getOrElse(Right(()))
                  .map(_ => f0)
              }
            )
          }
        else
          EitherT.point[F, ArtifactError, File](f)
    } yield f0
    eitherT.run
  }
}
