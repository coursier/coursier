package coursier.util

abstract class PlatformTaskCompanion {

  implicit val schedulable: Schedulable[Task] =
    new TaskSchedulable {}

}
