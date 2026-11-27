package coursier.cli.params

import coursier.cache.CacheLogger
import coursier.cache.loggers.{FileTypeRefreshDisplay, RefreshLogger}

object PlatformOutputParams {

  def logger(params: OutputParams, byFileType: Boolean): CacheLogger = {

    val loggerFallbackMode =
      !params.progressBars && RefreshLogger.defaultFallbackMode

    RefreshLogger.create(
      System.err,
      if (byFileType)
        FileTypeRefreshDisplay.create(keepOnScreen = false)
      else
        RefreshLogger.defaultDisplay(
          loggerFallbackMode,
          quiet = params.verbosity == -1 || Option(System.getenv("CI")).nonEmpty
        ),
      logChanging = params.logChanging,
      logPickedVersions = params.logPickedVersions
    )
  }
}
