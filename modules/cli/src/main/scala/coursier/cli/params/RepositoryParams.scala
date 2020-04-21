package coursier.cli.params

import java.io.File
import java.nio.charset.StandardCharsets
import java.nio.file.Files

import cats.data.{NonEmptyList, Validated, ValidatedNel}
import cats.implicits._
import coursier.{Repositories, moduleString}
import coursier.cli.install.SharedChannelParams
import coursier.cli.options.RepositoryOptions
import coursier.core.Repository
import coursier.install.Channel
import coursier.ivy.IvyRepository
import coursier.maven.MavenRepository
import coursier.parse.RepositoryParser

final case class RepositoryParams(
  repositories: Seq[Repository],
  channels: SharedChannelParams
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

    val channelsV = SharedChannelParams(options.channelOptions)

    (repositoriesV, channelsV).mapN {
      (repos0, channels) =>

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
          channels
        )
    }
  }
}
