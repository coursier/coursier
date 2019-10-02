package coursier.cli.app

import java.io.FileInputStream
import java.nio.charset.StandardCharsets
import java.util.zip.ZipFile

import argonaut.{DecodeJson, Parse}
import coursier.{Dependency, Fetch}
import coursier.cache.Cache
import coursier.cache.internal.FileUtil
import coursier.cli.app.Codecs.{decodeObj, encodeObj}
import coursier.core.Repository
import coursier.util.{Artifact, Task}

object Channels {

  def find(
    channels: Seq[Channel],
    id: String,
    cache: Cache[Task],
    repositories: Seq[Repository]
  ): Option[(Channel, String, Array[Byte])] = {

    def fromModule(channel: Channel.FromModule): Option[(Channel, String, Array[Byte])] = {

      val files = Fetch(cache)
        .withDependencies(Seq(Dependency.of(channel.module, "latest.release")))
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

    def fromUrl(channel: Channel.FromUrl): Option[(Channel, String, Array[Byte])] = {

      val loggerOpt = cache.loggerOpt

      val a = Artifact(channel.url, Map.empty, Map.empty, changing = true, optional = false, authentication = None)

      val fetch = cache.file(a).run

      val task = loggerOpt match {
        case None => fetch
        case Some(logger) =>
          Task.delay(logger.init(sizeHint = Some(1))).flatMap { _ =>
            fetch.attempt.flatMap { a =>
              Task.delay(logger.stop()).flatMap { _ =>
                Task.fromEither(a)
              }
            }
          }
      }

      val e = task.unsafeRun()(cache.ec)

      e match {
        case Left(err) =>
          throw new Exception(s"Error getting ${channel.url}: ${err.describe}")
        case Right(f) =>
          val b = FileUtil.readFully(new FileInputStream(f))
          val s = new String(b, StandardCharsets.UTF_8)
          Parse.decodeEither(s)(DecodeJson.MapDecodeJson(decodeObj)) match {
            case Left(err) =>
              throw new Exception(s"Error decoding $f (${channel.url}): $err")
            case Right(m) =>
              m.get(id).map { obj =>
                (channel, s"$f#$id", encodeObj(obj).nospaces.getBytes(StandardCharsets.UTF_8))
              }
          }
      }
    }

    channels
      .toStream
      .flatMap {
        case m: Channel.FromModule =>
          fromModule(m).toStream
        case u: Channel.FromUrl =>
          fromUrl(u).toStream
      }
      .headOption
  }

}
