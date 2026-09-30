package coursierbuild.modules

/** A module that is only ever published locally (`publishLocal`, `publishM2Local`), never to a
  * remote repository, be it for snapshot or release versions.
  *
  * The publish workflow excludes them from the artifact selection it passes to the publish
  * commands.
  */
trait LocalOnlyPublishModule extends CoursierPublishModule
