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
) {
  // format: on

  @deprecated("Use the override accepting a coursier.version.VersionInterval instead", "2.1.25")
  def this(
    versionRange: coursier.core.VersionInterval,
    dependencies: Option[Seq[JavaOrScalaDependency]],
    repositories: Option[Seq[Repository]],
    mainClass: Option[String],
    defaultMainClass: Option[String],
    javaProperties: Option[Seq[(String, String)]],
    prebuiltLauncher: Option[String],
    prebuiltBinaries: Option[Map[String, String]],
    launcherType: Option[LauncherType]
  ) = this(
    VersionInterval(
      versionRange.from.map(_.repr).map(Version(_)),
      versionRange.to.map(_.repr).map(Version(_)),
      versionRange.fromIncluded,
      versionRange.toIncluded
    ),
    dependencies,
    repositories,
    mainClass,
    defaultMainClass,
    javaProperties,
    prebuiltLauncher,
    prebuiltBinaries,
    launcherType
  )
  @deprecated("Use the override accepting a coursier.version.VersionInterval instead", "2.1.25")
  def this(
    versionRange: coursier.core.VersionInterval,
    dependencies: Option[Seq[JavaOrScalaDependency]],
    repositories: Option[Seq[Repository]],
    mainClass: Option[String],
    defaultMainClass: Option[String],
    javaProperties: Option[Seq[(String, String)]],
    prebuiltLauncher: Option[String],
    prebuiltBinaries: Option[Map[String, String]]
  ) = this(
    VersionInterval(
      versionRange.from.map(_.repr).map(Version(_)),
      versionRange.to.map(_.repr).map(Version(_)),
      versionRange.fromIncluded,
      versionRange.toIncluded
    ),
    dependencies,
    repositories,
    mainClass,
    defaultMainClass,
    javaProperties,
    prebuiltLauncher,
    prebuiltBinaries
  )
  @deprecated("Use the override accepting a coursier.version.VersionInterval instead", "2.1.25")
  def this(
    versionRange: coursier.core.VersionInterval,
    dependencies: Option[Seq[JavaOrScalaDependency]],
    repositories: Option[Seq[Repository]],
    mainClass: Option[String],
    defaultMainClass: Option[String],
    javaProperties: Option[Seq[(String, String)]]
  ) = this(
    VersionInterval(
      versionRange.from.map(_.repr).map(Version(_)),
      versionRange.to.map(_.repr).map(Version(_)),
      versionRange.fromIncluded,
      versionRange.toIncluded
    ),
    dependencies,
    repositories,
    mainClass,
    defaultMainClass,
    javaProperties
  )
  @deprecated("Use the override accepting a coursier.version.VersionInterval instead", "2.1.25")
  def this(
    versionRange: coursier.core.VersionInterval
  ) = this(
    VersionInterval(
      versionRange.from.map(_.repr).map(Version(_)),
      versionRange.to.map(_.repr).map(Version(_)),
      versionRange.fromIncluded,
      versionRange.toIncluded
    )
  )

  @deprecated("Use versionRange0 instead", "2.1.25")
  def versionRange: coursier.core.VersionInterval =
    coursier.core.VersionInterval(
      versionRange0.from.map(_.repr).map(coursier.core.Version(_)),
      versionRange0.to.map(_.repr).map(coursier.core.Version(_)),
      versionRange0.fromIncluded,
      versionRange0.toIncluded
    )
  @deprecated("Use withVersionRange0 instead", "2.1.25")
  def withVersionRange(newVersionRange: VersionOverrideHelper.LegacyVersionInterval)
    : VersionOverride =
    withVersionRange0(
      VersionInterval(
        newVersionRange.from.map(_.repr).map(Version(_)),
        newVersionRange.to.map(_.repr).map(Version(_)),
        newVersionRange.fromIncluded,
        newVersionRange.toIncluded
      )
    )
}

object VersionOverride {

  @deprecated("Use the override accepting a coursier.version.VersionInterval instead", "2.1.25")
  def apply(
    versionRange: coursier.core.VersionInterval,
    dependencies: Option[Seq[JavaOrScalaDependency]],
    repositories: Option[Seq[Repository]],
    mainClass: Option[String],
    defaultMainClass: Option[String],
    javaProperties: Option[Seq[(String, String)]],
    prebuiltLauncher: Option[String],
    prebuiltBinaries: Option[Map[String, String]],
    launcherType: Option[LauncherType]
  ): VersionOverride = apply(
    VersionInterval(
      versionRange.from.map(_.repr).map(Version(_)),
      versionRange.to.map(_.repr).map(Version(_)),
      versionRange.fromIncluded,
      versionRange.toIncluded
    ),
    dependencies,
    repositories,
    mainClass,
    defaultMainClass,
    javaProperties,
    prebuiltLauncher,
    prebuiltBinaries,
    launcherType
  )
  @deprecated("Use the override accepting a coursier.version.VersionInterval instead", "2.1.25")
  def apply(
    versionRange: coursier.core.VersionInterval,
    dependencies: Option[Seq[JavaOrScalaDependency]],
    repositories: Option[Seq[Repository]],
    mainClass: Option[String],
    defaultMainClass: Option[String],
    javaProperties: Option[Seq[(String, String)]],
    prebuiltLauncher: Option[String],
    prebuiltBinaries: Option[Map[String, String]]
  ): VersionOverride = apply(
    VersionInterval(
      versionRange.from.map(_.repr).map(Version(_)),
      versionRange.to.map(_.repr).map(Version(_)),
      versionRange.fromIncluded,
      versionRange.toIncluded
    ),
    dependencies,
    repositories,
    mainClass,
    defaultMainClass,
    javaProperties,
    prebuiltLauncher,
    prebuiltBinaries
  )
  @deprecated("Use the override accepting a coursier.version.VersionInterval instead", "2.1.25")
  def apply(
    versionRange: coursier.core.VersionInterval,
    dependencies: Option[Seq[JavaOrScalaDependency]],
    repositories: Option[Seq[Repository]],
    mainClass: Option[String],
    defaultMainClass: Option[String],
    javaProperties: Option[Seq[(String, String)]]
  ): VersionOverride = apply(
    VersionInterval(
      versionRange.from.map(_.repr).map(Version(_)),
      versionRange.to.map(_.repr).map(Version(_)),
      versionRange.fromIncluded,
      versionRange.toIncluded
    ),
    dependencies,
    repositories,
    mainClass,
    defaultMainClass,
    javaProperties
  )
  @deprecated("Use the override accepting a coursier.version.VersionInterval instead", "2.1.25")
  def apply(
    versionRange: coursier.core.VersionInterval
  ): VersionOverride = apply(
    VersionInterval(
      versionRange.from.map(_.repr).map(Version(_)),
      versionRange.to.map(_.repr).map(Version(_)),
      versionRange.fromIncluded,
      versionRange.toIncluded
    )
  )
}
