package coursier.cli.params

import java.io.File
import java.nio.charset.StandardCharsets
import java.nio.file.Files

import cats.data.{NonEmptyList, Validated, ValidatedNel}
import cats.implicits._
import coursier.{Repositories, moduleString}
import coursier.cli.options.RepositoryOptions
import coursier.core.Repository
import coursier.install.Channel
import coursier.ivy.IvyRepository
import coursier.maven.{MavenRepository, SbtMavenRepository}
import coursier.parse.RepositoryParser

final case class RepositoryParams(
  repositories: Seq[Repository]
)

object RepositoryParams {

  def apply(
    options: RepositoryOptions,
    hasSbtPlugins: Boolean = false
  ): ValidatedNel[String, RepositoryParams] = {

    val noDefaultShortcut = options.repository.exists(_.startsWith("!"))
    val repositoryInput   = options.repository.map(_.stripPrefix("!")).filter(_.nonEmpty)
    val repositoriesV = Validated.fromEither(
      RepositoryParser.repositories(repositoryInput)
        .either
        .left
        .map {
          case h :: t => NonEmptyList(h, t)
        }
    )

    repositoriesV.map { repos0 =>
      // preprend defaults
      val defaults =
        if (options.noDefault || noDefaultShortcut) Nil
        else {
          val extra =
            if (hasSbtPlugins) Seq(Repositories.sbtPlugin("releases"))
            else Nil
          coursier.Resolve.defaultRepositories ++ extra
        }
      var repos = defaults ++ repos0

      // take sbtPluginHack into account
      if (options.sbtPluginHack)
        repos = repos.map {
          case m: MavenRepository => SbtMavenRepository(m)
          case other              => other
        }

      // take dropInfoAttr into account
      if (options.dropInfoAttr)
        repos = repos.map {
          case m: IvyRepository => m.withDropInfoAttributes(true)
          case other            => other
        }

      RepositoryParams(repos)
    }
  }
}
