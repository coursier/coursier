package coursier.cache

import coursier.util.{Artifact, EitherT}

import java.io.File

abstract class PlatformCache[F[_]] {

  /** This method computes the task needed to get a file. */
  def file(artifact: Artifact): EitherT[F, ArtifactError, File]

}
