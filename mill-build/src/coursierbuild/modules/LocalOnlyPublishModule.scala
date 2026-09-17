package coursierbuild.modules

/** A module that is only ever published locally (`publishLocal`, `publishM2Local`), never to a
  * remote repository, be it for snapshot or release versions.
  *
  * `ci.publishSonatypeCentral` leaves these modules out by default, and the publish workflow
  * excludes them from any explicit selection it passes to it.
  */
trait LocalOnlyPublishModule extends CoursierPublishModule
