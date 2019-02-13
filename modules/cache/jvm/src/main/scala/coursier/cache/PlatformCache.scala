package coursier.cache

import java.io.File

import coursier.core.Artifact
import coursier.util.EitherT

abstract class PlatformCache[F[_]] {

  /** This method computes the task needed to get a file. */
  def file(artifact: Artifact): EitherT[F, ArtifactError, File]

}
