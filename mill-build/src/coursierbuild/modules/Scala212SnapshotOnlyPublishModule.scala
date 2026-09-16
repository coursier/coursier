package coursierbuild.modules

import mill.scalalib.CrossScalaModule

/** A cross-built module whose Scala 2.12 artifacts are only published for snapshot versions.
  *
  * Nothing consumes the Scala 2.12 artifacts of coursier from Maven Central any more: the only
  * Scala 2.12 users are sbt plugins, and sbt-coursier builds coursier from its sources and
  * publishes it locally itself. These modules are still cross-built and tested for Scala 2.12, can
  * still be published locally, and their Scala 2.12 snapshots are published, but their releases are
  * only published for the other Scala versions.
  */
trait Scala212SnapshotOnlyPublishModule extends SnapshotOnlyPublishModule with CrossScalaModule {
  def publishReleases = !crossScalaVersion.startsWith("2.12.")
}
