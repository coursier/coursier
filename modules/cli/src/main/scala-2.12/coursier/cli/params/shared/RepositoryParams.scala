package coursier.cli.params.shared

import cats.data.{NonEmptyList, Validated, ValidatedNel}
import coursier.cache.{CacheDefaults, CacheParse}
import coursier.cli.options.shared.RepositoryOptions
import coursier.core.Repository
import coursier.ivy.IvyRepository
import coursier.maven.MavenRepository
import coursier.util.Repositories


object RepositoryParams {

  def apply(options: RepositoryOptions, hasSbtPlugins: Boolean): ValidatedNel[String, Seq[Repository]] = {

    val repositoriesV = Validated.fromEither(
      CacheParse.repositories(options.repository)
        .either
        .left
        .map {
          case h :: t => NonEmptyList(h, t)
        }
    )

    repositoriesV.map { repos0 =>

      // preprend defaults
      val defaults =
        if (options.noDefault) Nil
        else {
          val extra =
            if (hasSbtPlugins) Seq(Repositories.sbtPlugin("releases"))
            else Nil
          CacheDefaults.defaultRepositories ++ extra
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

      repos
    }
  }
}
