package coursier.internal

import coursier.version.VersionConstraint

object Defaults {
  def scalaVersion: String =
    "2.12.8" // FIXME Get from sbt or scalac
  private lazy val scalaVersionConstraint0: VersionConstraint =
    VersionConstraint(scalaVersion)
  def scalaVersionConstraint: VersionConstraint =
    scalaVersionConstraint0
}
