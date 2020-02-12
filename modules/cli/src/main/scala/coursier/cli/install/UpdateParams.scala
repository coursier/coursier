package coursier.cli.install

import java.nio.file.{Path, Paths}
import java.util.concurrent.TimeUnit

import cats.data.ValidatedNel
import cats.implicits._
import coursier.cli.jvm.SharedJavaParams
import coursier.cli.params.OutputParams
import coursier.core.Repository
import coursier.params.CacheParams

import scala.concurrent.duration.Duration

final case class UpdateParams(
  cache: CacheParams,
  output: OutputParams,
  shared: SharedInstallParams,
  sharedJava: SharedJavaParams,
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
    val sharedJavaV = SharedJavaParams(options.sharedJavaOptions)

    val force = options.force

    (cacheParamsV, outputV, sharedV, sharedJavaV).mapN { (cacheParams, output, shared, sharedJava) =>
      UpdateParams(
        cacheParams,
        output,
        shared,
        sharedJava,
        options.overrideRepositories,
        force
      )
    }
  }
}
