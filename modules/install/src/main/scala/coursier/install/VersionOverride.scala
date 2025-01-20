package coursier.install

import coursier.core.Repository
import coursier.parse.JavaOrScalaDependency
import coursier.version.{Version, VersionInterval}
import dataclass._

// format: off
@data class VersionOverride(
  versionRange0: VersionInterval,
  dependencies: Option[Seq[JavaOrScalaDependency]] = None,
  repositories: Option[Seq[Repository]] = None,
  mainClass: Option[String] = None,
  defaultMainClass: Option[String] = None,
  javaProperties: Option[Seq[(String, String)]] = None,
  @since("2.1.0-M4")
    prebuiltLauncher: Option[String] = None,
  prebuiltBinaries: Option[Map[String, String]] = None,
  @since("2.1.10")
    launcherType: Option[LauncherType] = None
)
// format: on
