package coursier.install

import dataclass._
import coursier.parse.JavaOrScalaDependency
import coursier.core.{Repository, VersionInterval}

@data class VersionOverride(
  versionRange: VersionInterval,
  dependencies: Option[Seq[JavaOrScalaDependency]] = None,
  repositories: Option[Seq[Repository]] = None,
  mainClass: Option[String] = None,
  defaultMainClass: Option[String] = None,
  javaProperties: Option[Seq[(String, String)]] = None
)
