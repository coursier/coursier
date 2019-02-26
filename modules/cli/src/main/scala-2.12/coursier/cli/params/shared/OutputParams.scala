package coursier.cli.params.shared

import caseapp.Tag
import cats.data.{Validated, ValidatedNel}
import coursier.cache.CacheLogger
import coursier.cache.loggers.{FallbackRefreshDisplay, RefreshLogger}
import coursier.cli.options.shared.OutputOptions

final case class OutputParams(
  verbosity: Int,
  progressBars: Boolean,
  forcePrint: Boolean
) {
  def logger(): CacheLogger = {

    val loggerFallbackMode =
      !progressBars && RefreshLogger.defaultFallbackMode

    if (verbosity >= -1)
      RefreshLogger.create(
        System.err,
        RefreshLogger.defaultDisplay(
          loggerFallbackMode,
          quiet = verbosity == -1 || sys.env.contains("CI")
        )
      )
    else
      CacheLogger.nop
  }
}

object OutputParams {
  def apply(options: OutputOptions): ValidatedNel[String, OutputParams] = {

    val verbosityV =
      if (Tag.unwrap(options.quiet) > 0 && Tag.unwrap(options.verbose) > 0)
        Validated.invalidNel("Cannot have both quiet, and verbosity > 0")
      else
        Validated.validNel(Tag.unwrap(options.verbose) - Tag.unwrap(options.quiet))

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
