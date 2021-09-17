package coursier.cli.search

import cats.data.{NonEmptyList, Validated, ValidatedNel}
import cats.implicits._
import coursier.install.Channel
import coursier.parse.RepositoryParser
import coursier.core.Repository
import coursier.cli.params.{CacheParams, OutputParams, RepositoryParams}
import coursier.cli.install.SharedChannelParams

final case class SearchParams(
  cache: CacheParams,
  output: OutputParams,
  repositories: Seq[Repository],
  channels: Seq[Channel]
)

object SearchParams {

  def apply(options: SearchOptions, anyArg: Boolean): ValidatedNel[String, SearchParams] = {

    val cacheParamsV  = options.cacheOptions.params(None)
    val outputV       = OutputParams(options.outputOptions)
    val repositoriesV = RepositoryParams(options.repositoryOptions)

    (cacheParamsV, outputV, repositoriesV).mapN {
      (cacheParams, output, repositories) =>
        SearchParams(
          cacheParams,
          output,
          repositories.repositories,
          repositories.channels.channels
        )
    }
  }
}
