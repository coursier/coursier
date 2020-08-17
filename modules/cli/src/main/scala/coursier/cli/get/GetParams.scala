package coursier.cli.get

import cats.data.{Validated, ValidatedNel}
import cats.implicits._
import coursier.cli.params.{CacheParams, OutputParams}

final case class GetParams(
  cache: CacheParams,
  output: OutputParams,
  separator: String,
  force: Boolean,
  changing: Boolean
)

object GetParams {
  def apply(options: GetOptions): ValidatedNel[String, GetParams] = {
    val cacheV = options.cache.params
    val outputV = OutputParams(options.output)

    val separatorV = (options.zero, options.separator) match {
      case (false, None) => Validated.validNel(System.lineSeparator)
      case (false, Some(sep)) => Validated.validNel(sep)
      case (true, None) => Validated.validNel("\u0000")
      case (true, Some("\u0000")) => Validated.validNel("\u0000")
      case (true, Some(_)) => Validated.invalidNel("--zero and --separator cannot be specific at the same time")
    }

    (cacheV, outputV, separatorV).mapN {
      case (cache, output, separator) =>
        GetParams(
          cache,
          output,
          separator,
          options.force,
          options.changing
        )
    }
  }
}
