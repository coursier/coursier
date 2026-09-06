package coursier.cache.internal

import java.io.File

import coursier.cache.ArtifactError
import dataclass.data

@data case class DownloadResult(
  url: String,
  file: File,
  errorOpt: Option[ArtifactError] = None
)
