package coursier.cli.install

import java.nio.file.{Path, Paths}
import java.util.concurrent.TimeUnit

import cats.data.ValidatedNel
import cats.implicits._
import coursier.cli.params.OutputParams
import coursier.core.Repository
import coursier.params.CacheParams

import scala.concurrent.duration.Duration

final case class UpdateParams(
  cache: CacheParams,
  output: OutputParams,
  shared: SharedInstallParams,
  overrideRepositories: Boolean,
  force: Boolean
) {
  def selectedRepositories(initialList: Seq[Repository]): Seq[Repository] =
    if (overrideRepositories) shared.repositories
    else initialList
}

object UpdateParams {
  def apply(options: UpdateOptions): ValidatedNel[String, UpdateParams] = {

    val cacheParamsV = options.cacheOptions.params(Some(Duration(0L, TimeUnit.MILLISECONDS)))
    val outputV = OutputParams(options.outputOptions)

    val sharedV = SharedInstallParams(options.sharedInstallOptions)

    val force = options.force

    (cacheParamsV, outputV, sharedV).mapN { (cacheParams, output, shared) =>
      UpdateParams(
        cacheParams,
        output,
        shared,
        options.overrideRepositories,
        force
      )
    }
  }
}
