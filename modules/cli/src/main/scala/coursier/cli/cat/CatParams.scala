package coursier.cli.cat

import cats.data.ValidatedNel
import cats.implicits._
import coursier.cli.params.{CacheParams, OutputParams}

final case class CatParams(
  cache: CacheParams,
  output: OutputParams,
  changing: Option[Boolean]
)

object CatParams {
  def apply(options: CatOptions): ValidatedNel[String, CatParams] = {
    val cacheV  = options.cache.params
    val outputV = OutputParams(options.output)

    (cacheV, outputV).mapN {
      case (cache, output) =>
        CatParams(
          cache,
          output,
          options.changing
        )
    }
  }
}
