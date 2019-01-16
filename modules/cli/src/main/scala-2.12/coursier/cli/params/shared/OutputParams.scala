package coursier.cli.params.shared

import caseapp.Tag
import cats.data.{Validated, ValidatedNel}
import coursier.cli.options.shared.OutputOptions

final case class OutputParams(
  verbosity: Int,
  progressBars: Boolean,
  forcePrint: Boolean
)

object OutputParams {
  def apply(options: OutputOptions): ValidatedNel[String, OutputParams] = {

    val verbosityV =
      if (options.quiet && Tag.unwrap(options.verbose) > 0)
        Validated.invalidNel("Cannot have both quiet, and verbosity > 0")
      else if (options.quiet)
        Validated.validNel(-1)
      else
        Validated.validNel(Tag.unwrap(options.verbose))

    val progressBars = options.progress
    val forcePrint = options.force

    verbosityV.map { verbosity =>
      OutputParams(
        verbosity,
        progressBars,
        forcePrint
      )
    }
  }
}
