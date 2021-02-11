package coursier.cli.install

import java.nio.file.{Path, Paths}
import java.util.concurrent.TimeUnit

import cats.data.ValidatedNel
import cats.implicits._
import coursier.cli.jvm.SharedJavaParams
import coursier.cli.params.{CacheParams, OutputParams, RepositoryParams}
import coursier.core.Repository

import scala.concurrent.duration.Duration

final case class UpdateParams(
  cache: CacheParams,
  output: OutputParams,
  shared: SharedInstallParams,
  sharedJava: SharedJavaParams,
  repository: RepositoryParams,
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

    val repoV = RepositoryParams(options.repositoryOptions)

    val force = options.force

    (cacheParamsV, outputV, sharedV, sharedJavaV, repoV).mapN { (cacheParams, output, shared, sharedJava, repo) =>
      UpdateParams(
        cacheParams,
        output,
        shared,
        sharedJava,
        repo,
        options.overrideRepositories,
        force
      )
    }
  }
}
