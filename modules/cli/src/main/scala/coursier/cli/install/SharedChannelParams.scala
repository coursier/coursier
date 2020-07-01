package coursier.cli.install

import java.io.File
import java.nio.charset.StandardCharsets
import java.nio.file.Files

import cats.data.{NonEmptyList, Validated, ValidatedNel}
import cats.implicits._
import coursier.install.Channel
import coursier.install.Channels

final case class SharedChannelParams(
  channels: Seq[Channel]
)

object SharedChannelParams {
  def apply(options: SharedChannelOptions): ValidatedNel[String, SharedChannelParams] = {

    val channelsV = options
      .channel
      .traverse { s =>
        val e = Channel.parse(s)
          .left.map(NonEmptyList.one)
        Validated.fromEither(e)
      }

    val defaultChannels =
      if (options.defaultChannels) Channels.defaultChannels
      else Nil

    val contribChannels =
      if (options.contrib) Channels.contribChannels
      else Nil

    val fileChannelsV =
      if (options.fileChannels) {
        val configDirs = coursier.paths.CoursierPaths.configDirectories().toSeq
        val files = configDirs
          .flatMap { configDir =>
            val channelDir = new File(configDir, "channels")
            Option(channelDir.listFiles()).getOrElse(Array.empty[File])
          }
          .filter(f => !f.getName.startsWith("."))
        val rawChannels = files.toList.flatMap { f =>
          val b = Files.readAllBytes(f.toPath)
          val s = new String(b, StandardCharsets.UTF_8)
          s.linesIterator.map(_.trim).filter(_.nonEmpty).toSeq
        }
        rawChannels.traverse { s =>
          val e = Channel.parse(s)
            .left.map(NonEmptyList.one)
          Validated.fromEither(e)
        }
      } else
        Validated.validNel(Nil)

    (channelsV, fileChannelsV).mapN {
      (channels, fileChannels) =>
        SharedChannelParams(
          (channels ++ fileChannels ++ defaultChannels ++ contribChannels).distinct
        )
    }
  }
}
