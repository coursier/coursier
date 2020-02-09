package coursier.install

import java.io.FileInputStream
import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Paths}
import java.util.zip.ZipFile

import argonaut.{DecodeJson, Parse}
import coursier.{Dependency, Fetch, moduleString}
import coursier.cache.{Cache, FileCache}
import coursier.cache.internal.FileUtil
import coursier.core.Repository
import coursier.install.Codecs.{decodeObj, encodeObj}
import coursier.ivy.IvyRepository
import coursier.maven.MavenRepository
import coursier.util.{Artifact, Task}
import dataclass._

@data class Channels(
  channels: Seq[Channel] = Channels.defaultChannels,
  repositories: Seq[Repository] = coursier.Resolve.defaultRepositories,
  cache: Cache[Task] = FileCache(),
  @since
  verbosity: Int = 0
) {

  def appDescriptor(id: String): Task[AppInfo] = {

    val (actualId, overrideVersionOpt) = {
      val idx = id.indexOf(':')
      if (idx < 0)
        (id, None)
      else
        (id.take(idx), Some(id.drop(idx + 1)))
    }

    for {

      t0 <- find(actualId).flatMap {
        case None => Task.fail(new Channels.AppNotFound(actualId, channels))
        case Some(res) => Task.point(res)
      }
      (channel, _, descRepr) = t0

      _ = if (verbosity >= 1)
        System.err.println(s"Found app $actualId in channel $channel")

      strRepr = new String(descRepr, StandardCharsets.UTF_8)

      t1 <- RawAppDescriptor.parse(strRepr) match {
        case Left(error) =>
          if (verbosity >= 2)
            System.err.println(s"Malformed app descriptor:\n$strRepr")
          Task.fail(new Channels.ErrorParsingAppDescriptor(actualId, channel, error))
        case Right(desc) =>
          val source = Source(repositories, channel, actualId)
          // FIXME Get raw repositories from or along with params.repositories
          Task.point((source, desc))
      }
      (source, rawDesc) = t1

      desc <- rawDesc.appDescriptor.toEither match {
        case Left(errors) => Task.fail(new Channels.ErrorProcessingAppDescriptor(actualId, channel, errors.toList))
        case Right(desc0) => Task.point(desc0)
      }

      repositoriesRepr0 = Channels.repositoriesRepr(repositories).toList
      sourceBytes = RawSource(repositoriesRepr0, source.channel.repr, actualId)
        .repr.getBytes(StandardCharsets.UTF_8)

    } yield {
      AppInfo(desc, descRepr, source, sourceBytes)
        .overrideVersion(overrideVersionOpt)
    }
  }

  def find(id: String): Task[Option[(Channel, String, Array[Byte])]] = {

    def fromModule(channel: Channel.FromModule): Task[Option[(Channel, String, Array[Byte])]] =
      for {
        files <- Fetch(cache)
          .withDependencies(Seq(Dependency(channel.module, "latest.release")))
          .withRepositories(repositories)
          .io

        res <- files
          .toStream
          .map { f =>
            Task.delay {
              var zf: ZipFile = null
              try {
                zf = new ZipFile(f)
                val path = s"$id.json"
                Option(zf.getEntry(path))
                  .map { e =>
                    (channel, s"$f!$path", FileUtil.readFully(zf.getInputStream(e)))
                  }
              } finally {
                if (zf == null)
                  zf.close()
              }
            }
          }
          .foldLeft(Task.point(Option.empty[(Channel, String, Array[Byte])])) { (acc, elem) =>
            acc.flatMap {
              case None => elem
              case s @ Some(_) => Task.point(s)
            }
          }
      } yield res

    def fromUrl(channel: Channel.FromUrl): Task[Option[(Channel, String, Array[Byte])]] = {

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

      for {
        e <- task
        f <- Task.fromEither(e.left.map(err => new Exception(s"Error getting ${channel.url}", err)))
        content <- Task.delay {
          val b = FileUtil.readFully(new FileInputStream(f))
          new String(b, StandardCharsets.UTF_8)
        }
        m <- Task.fromEither {
          Parse.decodeEither(content)(DecodeJson.MapDecodeJson(decodeObj))
            .left.map(err => new Exception(s"Error decoding $f (${channel.url}): $err"))
        }
      } yield {
        m.get(id).map { obj =>
          (channel, s"$f#$id", encodeObj(obj).nospaces.getBytes(StandardCharsets.UTF_8))
        }
      }
    }

    def fromDirectory(channel: Channel.FromDirectory): Task[Option[(Channel, String, Array[Byte])]] = {

      val f = channel.path.resolve(s"$id.json")

      for {
        contentOpt <- Task.delay {
          if (Files.isRegularFile(f)) {
            val b = Files.readAllBytes(f)
            Some(new String(b, StandardCharsets.UTF_8))
          } else
            None
        }
        objOpt <- Task.fromEither {
          contentOpt match {
            case None => Right(None)
            case Some(content) =>
              Parse.decodeEither(content)(decodeObj)
                .left.map(err => new Exception(s"Error decoding $f: $err"))
                .map(Some(_))
          }
        }
      } yield {
        objOpt.map { obj =>
          (channel, f.toString, encodeObj(obj).nospaces.getBytes(StandardCharsets.UTF_8))
        }
      }
    }

    channels
      .toStream
      .map {
        case m: Channel.FromModule =>
          fromModule(m)
        case u: Channel.FromUrl =>
          fromUrl(u)
        case d: Channel.FromDirectory =>
          fromDirectory(d)
      }
      .foldLeft(Task.point(Option.empty[(Channel, String, Array[Byte])])) { (acc, elem) =>
        acc.flatMap {
          case None => elem
          case s @ Some(_) => Task.point(s)
        }
      }
  }

}

object Channels {

  private lazy val defaultChannels0 =
    // TODO Allow to customize that via env vars / Java properties
    Seq(
      Channel.module(mod"io.get-coursier:apps")
    )

  def defaultChannels: Seq[Channel] =
    defaultChannels0

  private def repositoriesRepr(repositories: Seq[Repository]): Seq[String] =
    repositories.toList.flatMap {
      case m: MavenRepository =>
        // FIXME This discards authentication, …
        List(m.root)
      case i: IvyRepository =>
        // FIXME This discards authentication, metadataPattern, …
        List(s"ivy:${i.pattern.string}")
      case _ =>
        // ???
        Nil
    }

  sealed abstract class ChannelsException(message: String, cause: Throwable = null)
    extends Exception(message, cause)

  final class AppNotFound(val id: String, val channels: Seq[Channel])
    extends ChannelsException(
      s"Cannot find app $id in channels ${channels.map(_.repr).mkString(", ")}"
    )

  final class ErrorParsingAppDescriptor(val id: String, val channel: Channel, val reason: String)
    extends ChannelsException(
      s"Error parsing app descriptor for app $id from channel ${channel.repr}: $reason"
    )

  final class ErrorProcessingAppDescriptor(val id: String, val channel: Channel, val errors: Seq[String])
    extends ChannelsException(
      s"Error processing app descriptor for app $id from channel ${channel.repr}: ${errors.mkString(", ")}"
    )

}
