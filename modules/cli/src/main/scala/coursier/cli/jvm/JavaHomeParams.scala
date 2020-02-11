package coursier.cli.jvm

import cats.data.ValidatedNel
import cats.implicits._
import coursier.cli.params.OutputParams
import coursier.params.CacheParams

final case class JavaHomeParams(
  shared: SharedJavaParams,
  cache: CacheParams,
  output: OutputParams
)

object JavaHomeParams {
  def apply(options: JavaHomeOptions): ValidatedNel[String, JavaHomeParams] = {
    val sharedV = SharedJavaParams(options.sharedJavaOptions)
    val cacheV = options.cacheOptions.params
    val outputV = OutputParams(options.outputOptions)
    (sharedV, cacheV, outputV).mapN { (shared, cache, output) =>
      JavaHomeParams(
        shared,
        cache,
        output
      )
    }
  }
}
