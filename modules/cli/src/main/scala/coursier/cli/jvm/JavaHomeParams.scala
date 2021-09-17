package coursier.cli.jvm

import java.nio.file.{Path, Paths}

import cats.data.ValidatedNel
import cats.implicits._
import coursier.cli.params.{CacheParams, EnvParams, OutputParams, RepositoryParams}

final case class JavaHomeParams(
  shared: SharedJavaParams,
  repository: RepositoryParams,
  cache: CacheParams,
  output: OutputParams,
  env: EnvParams
)

object JavaHomeParams {
  def apply(options: JavaHomeOptions): ValidatedNel[String, JavaHomeParams] = {
    val sharedV = SharedJavaParams(options.sharedJavaOptions)
    val cacheV  = options.cacheOptions.params
    val outputV = OutputParams(options.outputOptions)
    val envV    = EnvParams(options.envOptions)
    val repoV   = RepositoryParams(options.repositoryOptions)
    (sharedV, cacheV, outputV, envV, repoV).mapN { (shared, cache, output, env, repo) =>
      JavaHomeParams(
        shared,
        repo,
        cache,
        output,
        env
      )
    }
  }
}
