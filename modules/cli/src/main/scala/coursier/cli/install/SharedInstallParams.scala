package coursier.cli.install

import java.nio.file.{Path, Paths}

import caseapp.Tag
import cats.data.ValidatedNel
import coursier.cache.CacheLogger
import coursier.cli.params.OutputParams
import coursier.install.GraalvmParams
import coursier.params.CacheParams

import scala.concurrent.duration.Duration

final case class SharedInstallParams(
  cache: CacheParams,
  verbosity: Int,
  progressBars: Boolean,
  dir: Path,
  forceUpdate: Boolean,
  graalvmParamsOpt: Option[GraalvmParams] = None
) {
  def logger(): CacheLogger =
    OutputParams(verbosity, progressBars, forcePrint = false).logger()
}

object SharedInstallParams {
  def apply(options: SharedInstallOptions): ValidatedNel[String, SharedInstallParams] =
    apply(options, None)
  def apply(options: SharedInstallOptions, defaultTtlOpt: Option[Duration]): ValidatedNel[String, SharedInstallParams] = {

    val cacheParamsV = options.cacheOptions.params(defaultTtlOpt)

    val verbosity = Tag.unwrap(options.verbose) - Tag.unwrap(options.quiet)

    val progressBars = options.progress

    val dir = options.dir match {
      case Some(d) => Paths.get(d)
      case None => InstallParams.defaultDir
    }

    val graalvmParams = {
      val homeOpt = options.graalvmHome.orElse(sys.env.get("GRAALVM_HOME"))
      GraalvmParams(homeOpt, options.graalvmOption)
    }

    cacheParamsV.map { cacheParams =>
      SharedInstallParams(
        cacheParams,
        verbosity,
        progressBars,
        dir,
        options.forceUpdate,
        Some(graalvmParams)
      )
    }
  }
}
