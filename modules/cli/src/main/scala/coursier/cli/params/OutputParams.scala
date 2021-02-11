package coursier.cli.params

import caseapp.Tag
import cats.data.{Validated, ValidatedNel}
import cats.implicits._
import coursier.cache.CacheLogger
import coursier.cache.loggers.RefreshLogger
import coursier.cli.options.OutputOptions
import coursier.cache.loggers.FileTypeRefreshDisplay

final case class OutputParams(
  verbosity: Int,
  progressBars: Boolean,
  logChanging: Boolean
) {
  def logger(): CacheLogger =
    logger(byFileType = false)
  def logger(byFileType: Boolean): CacheLogger = {

    val loggerFallbackMode =
      !progressBars && RefreshLogger.defaultFallbackMode

    if (verbosity >= -1)
      RefreshLogger.create(
        System.err,
        if (byFileType)
          FileTypeRefreshDisplay.create(keepOnScreen = false)
        else
          RefreshLogger.defaultDisplay(
            loggerFallbackMode,
            quiet = verbosity == -1 || Option(System.getenv("CI")).nonEmpty
          ),
        logChanging = logChanging
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

    val verbosityLogChangingCheckV =
      if (options.logChanging && verbosityV.toOption.exists(_ < 0))
        Validated.invalidNel("Cannot be both quiet and log changing artifacts")
      else
        Validated.validNel(())

    val progressBars = options.progress
    val logChanging = options.logChanging

    (verbosityV, verbosityLogChangingCheckV).mapN {
      (verbosity, _) =>
        OutputParams(
          verbosity,
          progressBars,
          logChanging
        )
    }
  }
}
