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

  def loggerOpt: Option[CacheLogger] =
    this match {
      case withLogger: Cache.WithLogger[_, _] =>
        Some(withLogger.logger)
      case _ =>
        None
    }
}
