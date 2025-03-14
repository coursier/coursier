package coursier.cache

import coursier.cache.internal.Retry
import coursier.util.{Sync, Task}
import dataclass.data

import java.nio.file.{Files, Path}
import java.nio.file.StandardCopyOption

@data class DigestBasedCache[F[_]](
  location: Path,
  retry: Retry =
    Retry(
      CacheDefaults.retryCount,
      CacheDefaults.retryBackoffInitialDelay,
      CacheDefaults.retryBackoffMultiplier
    )
)(implicit
  sync: Sync[F]
) {
  private def pathFor(digest: String): Path = {
    assert(digest.length > 2)
    location.resolve(s"${digest.take(2)}/${digest.drop(2)}")
  }
  def `import`(artifact: DigestArtifact): Path = {
    val path = pathFor(artifact.digest)
    if (Files.exists(path)) path
    else
      CacheLocks.withLockOr(location.toFile, path.toFile, retry)(
        {
          if (!Files.exists(path)) {
            Files.createDirectories(path.getParent)
            val tmpPath = path.getParent.resolve("." + path.getFileName.toString + ".tmp")
            try {
              Files.deleteIfExists(tmpPath)
              Files.copy(artifact.path, tmpPath)
              Files.move(tmpPath, path, StandardCopyOption.ATOMIC_MOVE)
            }
            finally
              Files.deleteIfExists(tmpPath)
          }
          path
        },
        None
      )
  }
  def get(digest: String): Option[Path] = {
    val path = pathFor(digest)
    if (Files.exists(path)) Some(path)
    else None
  }
}

object DigestBasedCache {
  def apply(): DigestBasedCache[Task] =
    new DigestBasedCache(CacheDefaults.digestBasedCacheLocation.toPath)
}
