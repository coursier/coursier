package coursier.cache

import coursier.util.Task

object PriviledgedArchiveCacheTests extends ArchiveCacheTests {
  override def archiveCache(location: os.Path): ArchiveCache[Task] =
    ArchiveCache.priviledged[Task]()
      .withLocation(location.toIO)
      .withUnArchiver(UnArchiver.priviledgedTestMode())
  override def notOnWindows = true
}
