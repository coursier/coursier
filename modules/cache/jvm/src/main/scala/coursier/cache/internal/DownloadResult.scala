package coursier.cache.internal

import coursier.cache.ArtifactError
import dataclass.data

import java.io.File

@data class DownloadResult(
  url: String,
  file: File,
  errorOpt: Option[ArtifactError] = None
)
