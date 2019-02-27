package coursier.util

abstract class PlatformTaskCompanion {

  implicit val sync: Sync[Task] =
    new TaskSync {}

}
