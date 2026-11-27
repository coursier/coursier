package coursier.cli.params

import coursier.cache.CacheLogger
import coursier.cli.internal.LineLogger

object PlatformOutputParams {

  /** `byFileType` and the progress-bar knobs need `coursier.cache.loggers`, which is JVM-only -
    * [[LineLogger]] is the only display here.
    */
  def logger(params: OutputParams, byFileType: Boolean): CacheLogger =
    if (params.verbosity < 0)
      CacheLogger.nop
    else
      new LineLogger(params.logChanging, params.logPickedVersions)
}
