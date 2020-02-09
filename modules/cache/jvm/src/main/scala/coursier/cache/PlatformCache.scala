package coursier.cache

import java.nio.file.Path

import coursier.util.{Artifact, EitherT}

abstract class PlatformCache[F[_]] {

  /** This method computes the task needed to get a file. */
  def file(artifact: Artifact): EitherT[F, ArtifactError, Path]

}
