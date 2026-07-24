package coursier.cache.internal

import dataclass.data

import java.io.File

import coursier.cache.ArtifactError

@data case class DownloadResult(
  url: String,
  file: File,
  errorOpt: Option[ArtifactError] = None
)
