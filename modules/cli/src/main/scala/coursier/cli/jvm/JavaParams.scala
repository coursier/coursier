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
  def apply(options: JavaOptions): ValidatedNel[String, JavaParams] = {
    val sharedV = SharedJavaParams(options.sharedJavaOptions)
    val cacheV = options.cacheOptions.params
    val outputV = OutputParams(options.outputOptions)
    val envV = EnvParams(options.envOptions)

    val flagsV =
      if (Seq(options.installed, options.available).count(identity) > 1)
        Validated.invalidNel("Error: can only specify one of --installed, --available.")
      else
        Validated.validNel(())

    (sharedV, cacheV, outputV, envV, flagsV).mapN { (shared, cache, output, env, _) =>
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
