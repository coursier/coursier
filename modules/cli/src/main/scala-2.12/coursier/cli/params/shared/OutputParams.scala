package coursier.cli.params.shared

import java.io.OutputStreamWriter

import caseapp.Tag
import cats.data.{Validated, ValidatedNel}
import coursier.TermDisplay
import coursier.cache.CacheLogger
import coursier.cli.options.shared.OutputOptions

final case class OutputParams(
  verbosity: Int,
  progressBars: Boolean,
  forcePrint: Boolean
) {
  def logger(): CacheLogger = {

    val loggerFallbackMode =
      !progressBars && TermDisplay.defaultFallbackMode

    if (verbosity >= 0)
      new TermDisplay(
        new OutputStreamWriter(System.err),
        fallbackMode = loggerFallbackMode
      )
    else
      CacheLogger.nop
  }
}

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
