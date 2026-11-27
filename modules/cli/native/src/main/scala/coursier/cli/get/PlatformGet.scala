package coursier.cli.get

import coursier.cache.{ArtifactError, Cache}
import coursier.util.{Artifact, Task}

import java.io.File

object PlatformGet {

  def hiddenCommand: Boolean = false

  /** `archive-cache` is JVM-only, so this launcher goes through `FileCache` and nothing else -
    * `--archive`, and URLs holding a `!`, are rejected.
    */
  def archiveFetcher(
    archiveCacheLocation: File,
    cache: Cache[Task]
  ): Artifact => Task[Either[ArtifactError, File]] =
    artifact =>
      Task.point(Left(
        new ArtifactError.DownloadError(
          s"Archives are not supported by this launcher (${artifact.url})",
          None
        )
      ))
}
