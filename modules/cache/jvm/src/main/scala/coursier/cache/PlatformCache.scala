package coursier.cache

import java.io.File

import java.nio.file.Files

import coursier.cache.internal.ArchiveIntegrity
import coursier.util.{Artifact, EitherT, Sync}

abstract class PlatformCache[F[_]] {

  /** This method computes the task needed to get a file. */
  def file(artifact: Artifact): EitherT[F, ArtifactError, File]

  /** Like [[file]], also checking the integrity of archives
    *
    * Archives are checked using the checksums of their compression format (only supported for GZIP
    * and ZIP archives for now), and downloaded again if they're corrupted.
    *
    * This default implementation checks archives every time, and deletes corrupted ones before
    * downloading them again. [[FileCache]] keeps track of the archives it already checked, and
    * [[RemoteCache]] asks its cache server to check archives.
    */
  def fileWithIntegrityCheck(
    artifact: Artifact
  )(implicit S: Sync[F]): EitherT[F, ArtifactError, File] =
    for {
      f <- file(artifact)
      invalid <- EitherT(
        S.delay[Either[ArtifactError, Boolean]] {
          val invalid0 = ArchiveIntegrity.check(artifact.url, f).contains(false)
          if (invalid0)
            Files.delete(f.toPath)
          Right(invalid0)
        }
      )
      f0 <- if (invalid) file(artifact) else EitherT.point[F, ArtifactError, File](f)
    } yield f0

  /** Like [[file]], optionally checking the integrity of archives like [[fileWithIntegrityCheck]]
    * does, and checking that the file has the expected checksum
    *
    * This default implementation computes the checksum every time, and returns an
    * [[ArtifactError.UnexpectedChecksum]] if it's not the expected one, without deleting the file
    * nor downloading it again. [[FileCache]] persists checksums alongside the files it checks, and
    * deletes and downloads them again once if their checksum is wrong. [[RemoteCache]] asks its
    * cache server to check checksums.
    *
    * @param integrityCheck
    *   whether to check the integrity of `artifact`, if it's an archive, like
    *   [[fileWithIntegrityCheck]] does
    * @param expectedChecksum
    *   checksum `artifact` is expected to have, if any - if it doesn't have it, this returns an
    *   [[ArtifactError.UnexpectedChecksum]] (or an [[ArtifactError.DownloadError]] mentioning
    *   "wrong checksum" when the check is done by a cache server)
    */
  def fileWithChecks(
    artifact: Artifact,
    integrityCheck: Boolean,
    expectedChecksum: Option[ExpectedChecksum]
  )(implicit S: Sync[F]): EitherT[F, ArtifactError, File] = {
    val download = if (integrityCheck) fileWithIntegrityCheck(artifact) else file(artifact)
    expectedChecksum match {
      case None => download
      case Some(expected) =>
        download.flatMap { f =>
          EitherT(
            S.delay {
              ArchiveIntegrity.checkChecksum(f, expected, ArchiveIntegrity.computeDigest)
                .map(_ => f)
            }
          )
        }
    }
  }

  def loggerOpt: Option[CacheLogger] =
    this match {
      // WithLogger is defined in a class, but Cache is its only instance
      case withLogger: Cache.WithLogger[_, _] @unchecked =>
        Some(withLogger.logger)
      case _ =>
        None
    }
}
