package coursierbuild.modules

import mill.*
import mill.javalib.PublishModule.PublishData

/** A module whose artifacts are only published for snapshot versions.
  *
  * Its artifacts are not published for releases (unless `publishReleases` says otherwise), but
  * users can still try it out from a snapshot version.
  */
trait SnapshotOnlyPublishModule extends CoursierPublishModule {

  /** Whether the artifacts of this module are published for release versions too */
  def publishReleases: Boolean = false

  /** First release version that this module's artifacts were not published for
    *
    * Used by `CsMima`: there is nothing to check binary compatibility against from that version on.
    */
  def firstUnpublishedReleaseVersion: String = "2.1.25"

  // We rely on the version computed when the build is loaded, rather than on publishVersion(),
  // so that we can decide upfront whether to publish artifacts or not - the "don't publish"
  // task doesn't depend on any task compiling this module. checkPublishVersion below ensures
  // both versions agree on whether we're building a snapshot version or not.
  def publishArtifacts =
    if (CoursierPublishModule.buildVersionIsSnapshot || publishReleases)
      Task {
        checkPublishVersion(publishVersion())
        super.publishArtifacts()
      }
    else
      Task {
        checkPublishVersion(publishVersion())
        Task.log.info(
          s"Not publishing ${artifactId()}, as ${publishVersion()} is not a snapshot version"
        )
        PublishData(artifactMetadata(), Nil)
      }

  private def checkPublishVersion(version: String): Unit =
    if (CoursierPublishModule.isSnapshot(version) != CoursierPublishModule.buildVersionIsSnapshot)
      sys.error(
        s"Inconsistent versions: version computed when loading the build " +
          s"(${CoursierPublishModule.buildVersion}) is " +
          (if (CoursierPublishModule.buildVersionIsSnapshot) "a snapshot version"
           else "not a snapshot version") +
          s", while the publish version of ${moduleSegments.render} ($version) is " +
          (if (CoursierPublishModule.isSnapshot(version)) "a snapshot version"
           else "not a snapshot version")
      )
}
