package coursier.cli.jvm

import cats.data.{Validated, ValidatedNel}
import cats.implicits._
import coursier.cli.params.OutputParams
import coursier.params.CacheParams

final case class JavaParams(
  env: Boolean,
  installed: Boolean,
  available: Boolean,
  shared: SharedJavaParams,
  cache: CacheParams,
  output: OutputParams
)

object JavaParams {
  def apply(options: JavaOptions): ValidatedNel[String, JavaParams] = {
    val sharedV = SharedJavaParams(options.sharedJavaOptions)
    val cacheV = options.cacheOptions.params
    val outputV = OutputParams(options.outputOptions)

    val flagsV =
      if (Seq(options.env, options.installed, options.available).count(identity) > 1)
        Validated.invalidNel(s"Error: can only specify one of --env, --installed, --available.")
      else
        Validated.validNel(())

    (sharedV, cacheV, outputV, flagsV).mapN { (shared, cache, output, _) =>
      JavaParams(
        options.env,
        options.installed,
        options.available,
        shared,
        cache,
        output
      )
    }
  }
}
