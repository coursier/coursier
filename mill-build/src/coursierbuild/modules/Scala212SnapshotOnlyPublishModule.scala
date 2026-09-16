package coursierbuild.modules

import mill.scalalib.CrossScalaModule

/** A cross-built module whose Scala 2.12 artifacts are only published for snapshot versions.
  *
  * The modules sbt-coursier depends on are the only ones still cross-built for Scala 2.12 (see
  * `ScalaVersions`). Nothing consumes their Scala 2.12 artifacts from Maven Central any more
  * though: sbt-coursier builds coursier from its sources and publishes it locally itself. So these
  * modules can still be published locally, and their Scala 2.12 snapshots are published, but their
  * releases are only published for the other Scala versions.
  */
trait Scala212SnapshotOnlyPublishModule extends SnapshotOnlyPublishModule with CrossScalaModule {
  def publishReleases = !crossScalaVersion.startsWith("2.12.")
}
