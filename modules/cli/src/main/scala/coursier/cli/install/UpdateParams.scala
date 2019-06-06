package coursier.cli.install

import java.nio.file.{Path, Paths}
import java.util.concurrent.TimeUnit

import cats.data.ValidatedNel
import cats.implicits._
import coursier.core.Repository
import coursier.parse.RepositoryParser

import scala.concurrent.duration.Duration

final case class UpdateParams(
  shared: SharedInstallParams,
  repositories: Seq[Repository],
  overrideRepositories: Boolean,
  dir: Path
)

object UpdateParams {
  def apply(options: UpdateOptions): ValidatedNel[String, UpdateParams] = {

    import InstallParams.validationNelToCats

    val sharedV = SharedInstallParams(options.sharedInstallOptions, Some(Duration(0L, TimeUnit.MILLISECONDS)))

    val repositoriesV = validationNelToCats(RepositoryParser.repositories(options.repository))

    val defaultRepositories =
      if (options.defaultRepositories)
        coursier.Resolve.defaultRepositories
      else
        Nil

    val dir = options.dir
      .map(Paths.get(_))
      .getOrElse(InstallParams.defaultDir)

    (sharedV, repositoriesV).mapN {
      (shared, repositories) =>
        UpdateParams(
          shared,
          defaultRepositories ++ repositories,
          options.overrideRepositories,
          dir
        )
    }
  }
}
