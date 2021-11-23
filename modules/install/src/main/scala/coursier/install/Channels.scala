package coursier.install

import java.io.{File, FileInputStream}
import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Paths, Path}
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
import scala.collection.JavaConverters._

@data class Channels(
  channels: Seq[Channel] = Channels.defaultChannels,
  repositories: Seq[Repository] = coursier.Resolve.defaultRepositories,
  cache: Cache[Task] = FileCache(),
  @since
  verbosity: Int = 0,
  @since("2.0.10")
  logChannelVersion: Boolean = false
) {

  def appDescriptor(id: String): Task[AppInfo] = {

    val (inlineOpt, actualId, overrideVersionOpt) = {
      val idx = id.indexOf(':')
      if (idx < 0)
        (None, id, None)
      else if (id.length > idx + 1 && id.charAt(idx + 1) == '{')
        (Some(id.drop(idx + 1)), id.take(idx), None)
      else
        (None, id.take(idx), Some(id.drop(idx + 1)))
    }

    for {

      channelData <- {
        inlineOpt match {
          case None =>
            find(actualId).flatMap {
              case None       => Task.fail(new Channels.AppNotFound(actualId, channels))
              case Some(data) => Task.point(data)
            }
          case Some(inline) =>
            val data = ChannelData(
              Channel.Inline(),
              "",
              inline.getBytes(StandardCharsets.UTF_8)
            )
            Task.point(data)
        }
      }

      _ = if (verbosity >= 1)
        System.err.println(s"Found app $actualId in channel ${channelData.channel.repr}")

      t1 <- RawAppDescriptor.parse(channelData.strData) match {
        case Left(error) =>
          if (verbosity >= 2)
            System.err.println(s"Malformed app descriptor:\n${channelData.strData}")
          Task.fail(new Channels.ErrorParsingAppDescriptor(actualId, channelData.channel, error))
        case Right(desc) =>
          val source = Source(repositories, channelData.channel, actualId)
          // FIXME Get raw repositories from or along with params.repositories
          Task.point((source, desc))
      }
      (source, rawDesc) = t1

      desc <- rawDesc.appDescriptor.toEither match {
        case Left(errors) =>
          Task.fail {
            new Channels.ErrorProcessingAppDescriptor(
              actualId,
              channelData.channel,
              errors.toList
            )
          }
        case Right(desc0) => Task.point(desc0)
      }

      repositoriesRepr0 = Channels.repositoriesRepr(repositories).toList
      sourceBytes = RawSource(repositoriesRepr0, source.channel.repr, actualId)
        .repr.getBytes(StandardCharsets.UTF_8)

    } yield AppInfo(desc, channelData.data, source, sourceBytes)
      .overrideVersion(overrideVersionOpt)
  }

  private def withZipFile[T](file: File)(f: ZipFile => T): T = {
    var zf: ZipFile = null
    try {
      zf = new ZipFile(file)
      f(zf)
    }
    finally if (zf != null)
      zf.close()
  }

  def find(id: String): Task[Option[ChannelData]] = {

    def fromModule(channel: Channel.FromModule): Task[Option[ChannelData]] =
      for {
        res <- Fetch(cache)
          .withDependencies(Seq(Dependency(channel.module, channel.version)))
          .withRepositories(repositories)
          .ioResult

        _ = {
          for (logger <- cache.loggerOpt)
            logger.use {
              val retainedVersion =
                res.resolution.reconciledVersions.getOrElse(channel.module, "[unknown]")
              logger.pickedModuleVersion(channel.module.repr, retainedVersion)
            }
        }

        dataOpt <- res
          .files
          .iterator
          .map { f =>
            Task.delay {
              withZipFile(f) { zf =>
                val path = s"$id.json"
                Option(zf.getEntry(path)).map { e =>
                  ChannelData(
                    channel,
                    s"$f!$path",
                    FileUtil.readFully(zf.getInputStream(e))
                  )
                }
              }
            }
          }
          .foldLeft[Task[Option[ChannelData]]](Task.point(None)) { (acc, elem) =>
            acc.flatMap {
              case None        => elem
              case s @ Some(_) => Task.point(s)
            }
          }
      } yield dataOpt

    def fromUrl(channel: Channel.FromUrl): Task[Option[ChannelData]] = {

      val loggerOpt = cache.loggerOpt

      val a = Artifact(
        channel.url,
        Map.empty,
        Map.empty,
        changing = true,
        optional = false,
        authentication = None
      )

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
      } yield m.get(id).map { obj =>
        ChannelData(
          channel,
          s"$f#$id",
          encodeObj(obj).nospaces.getBytes(StandardCharsets.UTF_8)
        )
      }
    }

    def fromDirectory(channel: Channel.FromDirectory): Task[Option[ChannelData]] = {

      val f = channel.path.resolve(s"$id.json")

      for {
        contentOpt <- Task.delay {
          if (Files.isRegularFile(f)) {
            val b = Files.readAllBytes(f)
            Some(new String(b, StandardCharsets.UTF_8))
          }
          else
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
      } yield objOpt.map { obj =>
        ChannelData(
          channel,
          f.toString,
          encodeObj(obj).nospaces.getBytes(StandardCharsets.UTF_8)
        )
      }
    }

    channels
      .iterator
      .map {
        case m: Channel.FromModule =>
          fromModule(m)
        case u: Channel.FromUrl =>
          fromUrl(u)
        case d: Channel.FromDirectory =>
          fromDirectory(d)
        case _: Channel.Inline =>
          Task.point(None)
      }
      .foldLeft(Task.point(Option.empty[ChannelData])) { (acc, elem) =>
        acc.flatMap {
          case None        => elem
          case s @ Some(_) => Task.point(s)
        }
      }
  }

  def searchAppName(query: Seq[String]): Task[List[String]] = {

    def matchQuery(id: String): Boolean =
      query.isEmpty || query.exists(id.contains)

    def fromModule(channel: Channel.FromModule): Task[List[String]] =
      for {
        res <- Fetch(cache)
          .withDependencies(Seq(Dependency(channel.module, channel.version)))
          .withRepositories(repositories)
          .ioResult

        _ = {
          for (logger <- cache.loggerOpt)
            logger.use {
              val retainedVersion =
                res.resolution.reconciledVersions.getOrElse(channel.module, "[unknown]")
              logger.pickedModuleVersion(channel.module.repr, retainedVersion)
            }
        }

        dataOpt <- res
          .files
          .iterator
          .map { f =>
            Task.delay {
              withZipFile(f) { zf =>
                zf.entries()
                  .asScala
                  .map(_.getName())
                  .filter(_.endsWith(".json"))
                  .map(_.stripSuffix(".json"))
                  .filter(matchQuery)
                  .toList
              }
            }
          }
          .foldLeft[Task[List[String]]](Task.point(List.empty[String])) { (acc, e) =>
            for {
              a     <- acc
              extra <- e
            } yield a ++ extra
          }
      } yield dataOpt

    def fromUrl(channel: Channel.FromUrl): Task[List[String]] = {

      val loggerOpt = cache.loggerOpt

      val a = Artifact(
        channel.url,
        Map.empty,
        Map.empty,
        changing = true,
        optional = false,
        authentication = None
      )

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
      } yield m.keys.filter(matchQuery).toList

    }

    def fromDirectory(channel: Channel.FromDirectory): Task[List[String]] = Task.delay {
      if (Files.isDirectory(channel.path)) {
        var stream: java.util.stream.Stream[Path] = null
        try {
          stream = Files.find(
            channel.path,
            1,
            (p: Path, _) =>
              Files.isRegularFile(p) &&
              Files.isReadable(p) &&
              p.getFileName().toString().endsWith(".json")
          )
          stream
            .iterator()
            .asScala
            .map(_.getFileName().toString().stripSuffix(".json"))
            .filter(matchQuery)
            .toList
        }
        finally if (stream != null)
          stream.close()
      }
      else List.empty[String]
    }

    channels
      .iterator
      .map {
        case m: Channel.FromModule =>
          fromModule(m)
        case u: Channel.FromUrl =>
          fromUrl(u)
        case d: Channel.FromDirectory =>
          fromDirectory(d)
        case _: Channel.Inline =>
          Task.point(List.empty[String])
      }
      .foldLeft(Task.point(List.empty[String])) { (acc, e) =>
        for {
          a     <- acc
          extra <- e
        } yield a ++ extra
      }.map(_.sorted)
  }

}

object Channels {

  private lazy val defaultChannels0 =
    // TODO Allow to customize that via env vars / Java properties
    Seq(
      Channel.module(mod"io.get-coursier:apps")
    )

  private lazy val contribChannels0 =
    // TODO Allow to customize that via env vars / Java properties
    Seq(
      Channel.module(mod"io.get-coursier:apps-contrib")
    )

  def defaultChannels: Seq[Channel] =
    defaultChannels0
  def contribChannels: Seq[Channel] =
    contribChannels0

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

  final class ErrorProcessingAppDescriptor(
    val id: String,
    val channel: Channel,
    val errors: Seq[String]
  ) extends ChannelsException(
        s"Error processing app descriptor for app $id from channel ${channel.repr}: ${errors.mkString(", ")}"
      )

}
