package coursier.util

import coursier.core.Authentication
import dataclass.data

@data class Artifact(
  url: String,
  checksumUrls: Map[String, String] = Map.empty,
  extra: Map[String, Artifact] = Map.empty,
  changing: Boolean = false,
  optional: Boolean = false,
  authentication: Option[Authentication] = None
)
