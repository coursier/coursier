package coursier.error

import coursier.cache.ArtifactError
import coursier.util.Artifact

sealed abstract class FetchError(message: String, cause: Throwable = null) extends CoursierError(message, cause)

object FetchError {

  final class DownloadingArtifacts(val errors: Seq[(Artifact, ArtifactError)]) extends FetchError(
    "Error fetching artifacts:\n" +
      errors.map { case (a, e) => s"${a.url}: ${e.describe}\n" }.mkString,
    errors.headOption.map(_._2).orNull
  )

}
