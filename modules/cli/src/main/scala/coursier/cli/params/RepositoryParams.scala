package coursier.cli.params

import java.io.File
import java.nio.charset.StandardCharsets
import java.nio.file.Files

import cats.data.{NonEmptyList, Validated, ValidatedNel}
import cats.implicits._
import coursier.cli.app.Channel
import coursier.{Repositories, moduleString}
import coursier.cli.options.RepositoryOptions
import coursier.core.Repository
import coursier.ivy.IvyRepository
import coursier.maven.MavenRepository
import coursier.parse.RepositoryParser

final case class RepositoryParams(
  repositories: Seq[Repository],
  channels: Seq[Channel]
)

object RepositoryParams {

  def apply(options: RepositoryOptions, hasSbtPlugins: Boolean = false): ValidatedNel[String, RepositoryParams] = {

    val repositoriesV = Validated.fromEither(
      RepositoryParser.repositories(options.repository)
        .either
        .left
        .map {
          case h :: t => NonEmptyList(h, t)
        }
    )

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

    val fileChannelsV =
      if (options.fileChannels) {
        val configDir = coursier.paths.CoursierPaths.configDirectory()
        val channelDir = new File(configDir, "channels")
        val files = Option(channelDir.listFiles()).getOrElse(Array.empty[File])
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

    (repositoriesV, channelsV, fileChannelsV).mapN {
      (repos0, channels, fileChannels) =>

        // preprend defaults
        val defaults =
          if (options.noDefault) Nil
          else {
            val extra =
              if (hasSbtPlugins) Seq(Repositories.sbtPlugin("releases"))
              else Nil
            coursier.Resolve.defaultRepositories ++ extra
          }
        var repos = defaults ++ repos0

        // take sbtPluginHack into account
        repos = repos.map {
          case m: MavenRepository => m.withSbtAttrStub(options.sbtPluginHack)
          case other => other
        }

        // take dropInfoAttr into account
        if (options.dropInfoAttr)
          repos = repos.map {
            case m: IvyRepository => m.withDropInfoAttributes(true)
            case other => other
          }

        RepositoryParams(
          repos,
          (channels ++ fileChannels ++ defaultChannels).distinct
        )
    }
  }
}
