package coursier.cli.params

import caseapp.Tag
import cats.data.{Validated, ValidatedNel}
import coursier.cache.CacheLogger
import coursier.cache.loggers.RefreshLogger
import coursier.cli.options.OutputOptions
import coursier.cache.loggers.FileTypeRefreshDisplay

final case class OutputParams(
  verbosity: Int,
  progressBars: Boolean
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

    verbosityV.map { verbosity =>
      OutputParams(
        verbosity,
        progressBars
      )
    }
  }
}
