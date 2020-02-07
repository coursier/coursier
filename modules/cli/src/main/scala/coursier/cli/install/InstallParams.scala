package coursier.cli.install

import java.io.File
import java.nio.charset.StandardCharsets
import java.nio.file.Files

import cats.data.{NonEmptyList, Validated, ValidatedNel}
import cats.implicits._
import coursier.install.{Channel, RawAppDescriptor}
import coursier.moduleString
import coursier.core.Repository
import coursier.parse.RepositoryParser

final case class InstallParams(
  shared: SharedInstallParams,
  rawAppDescriptor: RawAppDescriptor,
  channels: Seq[Channel],
  repositories: Seq[Repository],
  nameOpt: Option[String],
  installChannels: Seq[String]
)

object InstallParams {

  lazy val defaultDir = {
    coursier.paths.CoursierPaths.dataLocalDirectory().toPath.resolve("bin")
  }

  private[install] implicit def validationNelToCats[L, R](v: coursier.util.ValidationNel[L, R]): ValidatedNel[L, R] =
    v.either match {
      case Left(h :: t) => Validated.invalid(NonEmptyList.of(h, t: _*))
      case Right(r) => Validated.validNel(r)
    }

  def apply(options: InstallOptions): ValidatedNel[String, InstallParams] = {

    val sharedV = SharedInstallParams(options.sharedInstallOptions)

    val rawAppDescriptor = options.appOptions.rawAppDescriptor

    val channelsV = options
      .channel
      .traverse { s =>
        val e = Channel.parse(s)
          .left.map(NonEmptyList.one)
        Validated.fromEither(e)
      }

    val defaultChannels =
      if (options.defaultChannels)
        Seq(
          Channel.module(mod"io.get-coursier:apps")
        )
      else Nil

    val repositoriesV = validationNelToCats(RepositoryParser.repositories(options.appOptions.repository))

    val defaultRepositories =
      if (options.defaultRepositories)
        coursier.Resolve.defaultRepositories
      else
        Nil

    val nameOpt = options.name.map(_.trim).filter(_.nonEmpty)

    val fileChannelsV =
      if (options.fileChannels) {
        val configDir = coursier.paths.CoursierPaths.configDirectory()
        val channelDir = new File(configDir, "channels")
        val files = Option(channelDir.listFiles())
          .getOrElse(Array.empty[File])
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

    val addChannelsV = options.addChannel.traverse { s =>
      val e = Channel.parse(s)
        .left.map(NonEmptyList.one)
        .map(c => (s, c))
      Validated.fromEither(e)
    }

    (sharedV, channelsV, fileChannelsV, addChannelsV, repositoriesV).mapN {
      (shared, channels, fileChannels, addChannels, repositories) =>
        InstallParams(
          shared,
          rawAppDescriptor,
          (channels ++ fileChannels ++ defaultChannels ++ addChannels.map(_._2)).distinct,
          defaultRepositories ++ repositories,
          nameOpt,
          addChannels.map(_._1)
        )
    }
  }
}
