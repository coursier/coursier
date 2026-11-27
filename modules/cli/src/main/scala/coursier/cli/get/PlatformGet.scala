package coursier.cli.get

import coursier.cache.{ArchiveCache, ArtifactError, Cache}
import coursier.util.{Artifact, Task}

import java.io.File

object PlatformGet {

  def hiddenCommand: Boolean = true

  def archiveFetcher(
    archiveCacheLocation: File,
    cache: Cache[Task]
  ): Artifact => Task[Either[ArtifactError, File]] = {
    lazy val archiveCache = ArchiveCache.create[Task](archiveCacheLocation)
      .copy(cache = cache)
    artifact => archiveCache.get(artifact)
  }
}
