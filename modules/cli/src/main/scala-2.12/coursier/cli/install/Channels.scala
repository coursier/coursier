package coursier.cli.install

import java.util.zip.ZipFile

import coursier.{Dependency, Fetch}
import coursier.cache.Cache
import coursier.cache.internal.FileUtil
import coursier.core.{Module, Repository}
import coursier.util.Task

object Channels {

  def find(
    channels: Seq[Module],
    id: String,
    cache: Cache[Task],
    repositories: Seq[Repository]
  ): Option[(Module, String, Array[Byte])] = {

    val res = Fetch(cache)
      .withDependencies(channels.map(m => Dependency(m, "latest.integration")))
      .withRepositories(repositories)
      .ioResult
      .unsafeRun()(cache.ec)

    res
      .detailedArtifacts
      .toStream
      .flatMap {
        case (dep, _, _, f) =>
          var zf: ZipFile = null
          try {
            zf = new ZipFile(f)
            val path = s"$id.json"
            Option(zf.getEntry(path))
              .map { e =>
                (dep.module, s"$f!$path", FileUtil.readFully(zf.getInputStream(e)))
              }
              .toStream
          } finally {
            if (zf == null)
              zf.close()
          }
      }
      .headOption
  }

}
