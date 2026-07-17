package coursier.cache.internal

import java.io.File

import coursier.cache.ArtifactError

final case class DownloadResult(
  url: String,
  file: File,
  errorOpt: Option[ArtifactError] = None
)
