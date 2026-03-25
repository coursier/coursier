package coursier.cache

import java.io.File

import coursier.util.{Artifact, EitherT}

abstract class PlatformCache[F[_]] {

  /** This method computes the task needed to get a file. */
  def file(artifact: Artifact): EitherT[F, ArtifactError, File]

  def loggerOpt: Option[CacheLogger] =
    this match {
      case withLogger: Cache.WithLogger[_, _] =>
        Some(withLogger.logger)
      case _ =>
        None
    }
}
