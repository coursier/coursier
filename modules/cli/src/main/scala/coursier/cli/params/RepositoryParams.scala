package coursier.cli.params

import cats.data.{NonEmptyList, Validated, ValidatedNel}
import cats.implicits._
import coursier.cli.install.Channel
import coursier.{Repositories, moduleString}
import coursier.cli.options.RepositoryOptions
import coursier.core.Repository
import coursier.ivy.IvyRepository
import coursier.maven.MavenRepository
import coursier.parse.{JavaOrScalaModule, ModuleParser, RepositoryParser}

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
        if (s.contains("://"))
          Validated.validNel(Channel.url(s))
        else {
          val e = ModuleParser.javaOrScalaModule(s)
            .right.flatMap {
              case j: JavaOrScalaModule.JavaModule => Right(Channel.module(j.module))
              case s: JavaOrScalaModule.ScalaModule => Left(s"Scala dependencies ($s) not accepted as channels")
            }
            .left.map(NonEmptyList.one)
          Validated.fromEither(e)
        }
      }

    val defaultChannels =
      if (options.defaultChannels)
        Seq(
          Channel.module(mod"io.get-coursier:apps")
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
