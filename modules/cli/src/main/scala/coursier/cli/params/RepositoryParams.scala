package coursier.cli.params

import cats.data.{NonEmptyList, Validated, ValidatedNel}
import cats.implicits._
import coursier.{Repositories, moduleString}
import coursier.cli.options.RepositoryOptions
import coursier.core.{Module, Repository}
import coursier.ivy.IvyRepository
import coursier.maven.MavenRepository
import coursier.parse.{JavaOrScalaModule, ModuleParser, RepositoryParser}

final case class RepositoryParams(
  repositories: Seq[Repository],
  channels: Seq[Module]
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

    val channelsV = Validated.fromEither {
      ModuleParser.javaOrScalaModules(options.channel)
        .either
        .left.map { case h :: t => NonEmptyList.of(h, t: _*) }
        .right.flatMap { modules =>
          modules
            .toList
            .traverse {
              case j: JavaOrScalaModule.JavaModule => Validated.validNel(j.module)
              case s: JavaOrScalaModule.ScalaModule => Validated.invalidNel(s"Scala dependencies ($s) not accepted as channels")
            }
            .toEither
      }
    }

    val defaultChannels =
      if (options.defaultChannels)
        Seq(
          mod"io.get-coursier:apps"
        )
      else Nil

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
          case m: MavenRepository => m.copy(sbtAttrStub = options.sbtPluginHack)
          case other => other
        }

        // take dropInfoAttr into account
        if (options.dropInfoAttr)
          repos = repos.map {
            case m: IvyRepository => m.copy(dropInfoAttributes = true)
            case other => other
          }

        RepositoryParams(
          repos,
          channels ++ defaultChannels
        )
    }
  }
}
