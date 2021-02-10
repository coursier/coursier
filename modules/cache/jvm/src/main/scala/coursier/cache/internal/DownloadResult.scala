package coursier.cache.internal

import java.io.File

import coursier.cache.ArtifactError
import dataclass.data

@data class DownloadResult(
  url: String,
  file: File,
  errorOpt: Option[ArtifactError] = None
)
