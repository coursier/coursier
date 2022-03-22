package coursier.cli.jvm

import cats.data.{Validated, ValidatedNel}
import cats.implicits._
import coursier.cli.params.{CacheParams, EnvParams, OutputParams, RepositoryParams}

final case class JavaParams(
  available: Boolean,
  installed: Boolean,
  shared: SharedJavaParams,
  repository: RepositoryParams,
  cache: CacheParams,
  output: OutputParams,
  env: EnvParams
)

object JavaParams {
  def apply(options: JavaOptions, anyArg: Boolean): ValidatedNel[String, JavaParams] = {
    val sharedV = SharedJavaParams(options.sharedJavaOptions)
    val cacheV  = options.cacheOptions.params
    val outputV = OutputParams(options.outputOptions)
    val envV    = EnvParams(options.envOptions)
    val repoV   = RepositoryParams(options.repositoryOptions)

    val flags = Seq(
      options.available,
      options.installed,
      envV.toOption.fold(false)(_.anyFlag)
    )
    val flagsV =
      if (flags.count(identity) > 1)
        Validated.invalidNel(
          "Error: can only specify one of --env, --setup, --available or --installed."
        )
      else
        Validated.validNel(())

    val checkArgsV =
      if (anyArg && flags.exists(identity))
        Validated.invalidNel(
          s"Error: unexpected arguments passed along --env, --setup, --available or installed."
        )
      else
        Validated.validNel(())

    (sharedV, cacheV, outputV, envV, repoV, flagsV, checkArgsV).mapN {
      (shared, cache, output, env, repo, _, _) =>
        JavaParams(
          options.available,
          options.installed,
          shared,
          repo,
          cache,
          output,
          env
        )
    }
  }
}
