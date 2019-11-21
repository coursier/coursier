package coursier.util

import coursier.core.Authentication
import dataclass.data

@data class Artifact(
  url: String,
  checksumUrls: Map[String, String],
  extra: Map[String, Artifact],
  changing: Boolean,
  optional: Boolean,
  authentication: Option[Authentication]
)
