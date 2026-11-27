package coursier.cache.internal

import java.io.File

import coursier.cache.ArtifactError
import dataclass.data

@data(
  deprecatedSetters = true,
  deprecatedSettersMessage = "Use copy instead",
  deprecatedSettersSince = "2.1.25"
) case class DownloadResult(
  url: String,
  file: File,
  errorOpt: Option[ArtifactError] = None
)
