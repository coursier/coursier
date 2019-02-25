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

    def default = RefreshLogger.create(
      System.err,
      RefreshLogger.defaultDisplay(loggerFallbackMode)
    )

    def quiet = RefreshLogger.create(
      System.err,
      new FallbackRefreshDisplay(quiet = true)
    )

    def nop = CacheLogger.nop

    if (verbosity >= 1 || (verbosity == 0 && !sys.env.contains("CI")))
      default
    else if (verbosity >= -1)
      quiet
    else
      nop
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
