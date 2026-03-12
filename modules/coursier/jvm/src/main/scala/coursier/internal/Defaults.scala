package coursier.internal

import coursier.version.VersionConstraint

object Defaults {
  def scalaVersion: String =
    scala.util.Properties.versionNumberString
  private lazy val scalaVersionConstraint0: VersionConstraint =
    VersionConstraint(scalaVersion)
  def scalaVersionConstraint: VersionConstraint =
    scalaVersionConstraint0
}
