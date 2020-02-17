package coursier.cli.jvm

import cats.data.{Validated, ValidatedNel}
import cats.implicits._
import coursier.cli.params.{CacheParams, EnvParams, OutputParams}

final case class JavaParams(
  installed: Boolean,
  available: Boolean,
  shared: SharedJavaParams,
  cache: CacheParams,
  output: OutputParams,
  env: EnvParams
)

object JavaParams {
  def apply(options: JavaOptions, anyArg: Boolean): ValidatedNel[String, JavaParams] = {
    val sharedV = SharedJavaParams(options.sharedJavaOptions)
    val cacheV = options.cacheOptions.params
    val outputV = OutputParams(options.outputOptions)
    val envV = EnvParams(options.envOptions)

    val flags = Seq(
      options.installed,
      options.available,
      envV.toOption.fold(false)(_.anyFlag)
    )
    val flagsV =
      if (flags.count(identity) > 1)
        Validated.invalidNel("Error: can only specify one of --env, --setup, --installed, --available.")
      else
        Validated.validNel(())

    val checkArgsV =
      if (anyArg && flags.exists(identity))
        Validated.invalidNel(s"Error: unexpected arguments passed along --env, --setup, --installed, or --available")
      else
        Validated.validNel(())

    (sharedV, cacheV, outputV, envV, flagsV, checkArgsV).mapN { (shared, cache, output, env, _, _) =>
      JavaParams(
        options.installed,
        options.available,
        shared,
        cache,
        output,
        env
      )
    }
  }
}
