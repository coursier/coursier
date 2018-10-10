package coursier.cli.params.shared

import cats.data.{NonEmptyList, Validated, ValidatedNel}
import coursier.{Cache, CacheParse}
import coursier.cli.options.shared.RepositoryOptions
import coursier.core.Repository
import coursier.ivy.IvyRepository
import coursier.maven.MavenRepository


object RepositoryParams {

  private val defaultRepositories = Seq(
    Cache.ivy2Local,
    MavenRepository("https://repo1.maven.org/maven2")
  )

  def apply(options: RepositoryOptions): ValidatedNel[String, Seq[Repository]] = {

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
      var repos = (if (options.noDefault) Nil else defaultRepositories) ++ repos0

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
