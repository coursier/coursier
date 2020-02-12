package coursier.cli.install

import java.nio.file.{Path, Paths}

import caseapp.Tag
import cats.data.{NonEmptyList, Validated, ValidatedNel}
import cats.implicits._
import coursier.cache.{Cache, CacheLogger}
import coursier.cli.params.OutputParams
import coursier.core.Repository
import coursier.install.{GraalvmParams, InstallDir}
import coursier.parse.RepositoryParser
import coursier.util.Task

final case class SharedInstallParams(
  repositories: Seq[Repository],
  dir: Path,
  graalvmParamsOpt: Option[GraalvmParams] = None,
  onlyPrebuilt: Boolean
) {

  def installDir(cache: Cache[Task]): InstallDir =
    InstallDir(dir, cache)
      .withGraalvmParamsOpt(graalvmParamsOpt)
      .withCoursierRepositories(repositories)
      .withOnlyPrebuilt(onlyPrebuilt)
}

object SharedInstallParams {

  lazy val defaultDir = {
    coursier.paths.CoursierPaths.dataLocalDirectory().toPath.resolve("bin")
  }

  private[install] implicit def validationNelToCats[L, R](v: coursier.util.ValidationNel[L, R]): ValidatedNel[L, R] =
    v.either match {
      case Left(h :: t) => Validated.invalid(NonEmptyList.of(h, t: _*))
      case Right(r) => Validated.validNel(r)
    }

  def apply(options: SharedInstallOptions): ValidatedNel[String, SharedInstallParams] = {

    val repositoriesV = validationNelToCats(RepositoryParser.repositories(options.repository))

    val defaultRepositories =
      if (options.defaultRepositories)
        coursier.Resolve.defaultRepositories
      else
        Nil

    val dir = options.installDir.filter(_.nonEmpty) match {
      case Some(d) => Paths.get(d)
      case None => defaultDir
    }

    val graalvmParams = GraalvmParams(
      options.graalvmDefaultVersion.filter(_.nonEmpty),
      options.graalvmOption
    )

    val onlyPrebuilt = options.onlyPrebuilt

    repositoriesV.map { repositories =>
      SharedInstallParams(
        defaultRepositories ++ repositories,
        dir,
        Some(graalvmParams),
        onlyPrebuilt
      )
    }
  }
}
