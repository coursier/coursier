package coursier.cli.search

import cats.data.{NonEmptyList, Validated, ValidatedNel}
import cats.implicits._
import coursier.core.Repository
import coursier.cli.install.SharedChannelParams
import coursier.cli.params.{CacheParams, OutputParams, RepositoryParams}
import coursier.install.Channel
import coursier.parse.RepositoryParser

final case class SearchParams(
  cache: CacheParams,
  output: OutputParams,
  repositories: RepositoryParams,
  channels: SharedChannelParams
)

object SearchParams {

  def apply(options: SearchOptions, anyArg: Boolean): ValidatedNel[String, SearchParams] = {

    val cacheParamsV  = options.cacheOptions.params(None)
    val outputV       = OutputParams(options.outputOptions)
    val repositoriesV = RepositoryParams(options.repositoryOptions)
    val channelsV     = SharedChannelParams(options.channelOptions)

    (cacheParamsV, outputV, repositoriesV, channelsV).mapN {
      (cacheParams, output, repositories, channels) =>
        SearchParams(
          cacheParams,
          output,
          repositories,
          channels
        )
    }
  }
}
