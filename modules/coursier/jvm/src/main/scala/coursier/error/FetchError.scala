package coursier.error

import coursier.cache.ArtifactError
import coursier.util.Artifact

sealed abstract class FetchError(message: String, cause: Throwable = null)
    extends CoursierError(message, cause)

object FetchError {

  // format: off
  final class DownloadingArtifacts(val errors: Seq[(Artifact, ArtifactError)]) extends FetchError(
    "Error fetching artifacts:" + System.lineSeparator() +
      errors.map { case (a, e) =>
        s"${a.url}: ${e.describe}" + System.lineSeparator()
      }.mkString,
    errors.headOption.map(_._2).orNull
  )
  // format: on

}
