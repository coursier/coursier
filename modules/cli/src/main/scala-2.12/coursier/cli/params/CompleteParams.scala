package coursier.cli.params

import caseapp.core.RemainingArgs
import cats.data.{Validated, ValidatedNel}
import cats.implicits._
import coursier.cli.options.CompleteOptions
import coursier.cli.params.shared.{OutputParams, RepositoryParams}
import coursier.core.Repository
import coursier.params.CacheParams

final case class CompleteParams(
  cache: CacheParams,
  output: OutputParams,
  repositories: Seq[Repository],
  toComplete: String,
  scalaVersion: Option[String],
  scalaBinaryVersion: Option[String]
)

object CompleteParams {
  def apply(options: CompleteOptions, args: RemainingArgs): ValidatedNel[String, CompleteParams] = {

    val cacheV = options.cacheOptions.params
    val outputV = OutputParams(options.outputOptions)
    val repositoriesV = RepositoryParams(options.repositoryOptions)

    val argV = args.all match {
      case Seq() =>
        Validated.invalidNel("No argument to complete passed")
      case Seq(arg) =>
        Validated.validNel(arg)
      case other =>
        Validated.invalidNel(s"Got ${other.length} arguments to complete, expected one.")
    }

    (cacheV, outputV, repositoriesV, argV).mapN {
      (cache, output, repositories, arg) =>
        CompleteParams(
          cache,
          output,
          repositories,
          arg,
          options.scalaVersion.map(_.trim).filter(_.nonEmpty),
          options.scalaBinaryVersion
        )
    }
  }
}
