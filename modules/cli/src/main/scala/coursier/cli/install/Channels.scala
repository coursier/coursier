package coursier.cli.install

import java.util.zip.ZipFile

import coursier.{Dependency, Fetch}
import coursier.cache.Cache
import coursier.cache.internal.FileUtil
import coursier.core.Repository
import coursier.util.Task

object Channels {

  def find(
    channels: Seq[Channel],
    id: String,
    cache: Cache[Task],
    repositories: Seq[Repository]
  ): Option[(Channel, String, Array[Byte])] = {

    def fromModule(channel: Channel.FromModule): Option[(Channel, String, Array[Byte])] = {

      val files = Fetch(cache)
        .withDependencies(Seq(Dependency(channel.module, "latest.integration")))
        .withRepositories(repositories)
        .io
        .unsafeRun()(cache.ec)

      files
        .toStream
        .flatMap { f =>
          var zf: ZipFile = null
          try {
            zf = new ZipFile(f)
            val path = s"$id.json"
            Option(zf.getEntry(path))
              .map { e =>
                (channel, s"$f!$path", FileUtil.readFully(zf.getInputStream(e)))
              }
              .toStream
          } finally {
            if (zf == null)
              zf.close()
          }
        }
        .headOption
    }

    channels
      .toStream
      .flatMap {
        case m: Channel.FromModule =>
          fromModule(m).toStream
      }
      .headOption
  }

}
