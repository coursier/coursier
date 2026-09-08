package coursierbuild.modules

import mill.*
import mill.javalib.PublishModule.PublishData

/** A module whose artifacts are only published for snapshot versions.
  *
  * Its artifacts are not published for releases, but users can still try it out from a snapshot
  * version.
  */
trait SnapshotOnlyPublishModule extends CoursierPublishModule {

  // We rely on the version computed when the build is loaded, rather than on publishVersion(),
  // so that we can decide upfront whether to publish artifacts or not - the "don't publish"
  // task doesn't depend on any task compiling this module. checkPublishVersion below ensures
  // both versions agree on whether we're building a snapshot version or not.
  def publishArtifacts =
    if (CoursierPublishModule.buildVersionIsSnapshot)
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
