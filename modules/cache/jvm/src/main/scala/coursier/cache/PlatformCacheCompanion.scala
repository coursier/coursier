package coursier.cache

import coursier.util.Task

abstract class PlatformCacheCompanion {

  lazy val default: Cache[Task] = FileCache()

}
