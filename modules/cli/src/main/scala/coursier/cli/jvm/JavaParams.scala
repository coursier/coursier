package coursier.cli.jvm

import cats.data.ValidatedNel
import cats.implicits._
import coursier.cli.params.OutputParams
import coursier.params.CacheParams

final case class JavaParams(
  env: Boolean,
  shared: SharedJavaParams,
  cache: CacheParams,
  output: OutputParams
)

object JavaParams {
  def apply(options: JavaOptions): ValidatedNel[String, JavaParams] = {
    val sharedV = SharedJavaParams(options.sharedJavaOptions)
    val cacheV = options.cacheOptions.params
    val outputV = OutputParams(options.outputOptions)
    (sharedV, cacheV, outputV).mapN { (shared, cache, output) =>
      JavaParams(
        options.env,
        shared,
        cache,
        output
      )
    }
  }
}
