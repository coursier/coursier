package coursier.cli.install

import java.nio.file.{Path, Paths}

import caseapp.Tag
import cats.data.{NonEmptyList, Validated, ValidatedNel}
import cats.implicits._
import coursier.cache.{Cache, CacheLogger}
import coursier.cli.params.OutputParams
import coursier.core.Repository
import coursier.install.{GraalvmParams, InstallDir, Platform}
import coursier.util.Task

final case class SharedInstallParams(
  dir: Path,
  graalvmParamsOpt: Option[GraalvmParams] = None,
  onlyPrebuilt: Boolean,
  platformOpt: Option[String],
  preferPrebuilt: Boolean,
  proguarded: Option[Boolean]
) {

  def installDir(cache: Cache[Task], repositories: Seq[Repository]): InstallDir =
    InstallDir(dir, cache)
      .withGraalvmParamsOpt(graalvmParamsOpt)
      .withCoursierRepositories(repositories)
      .withOnlyPrebuilt(onlyPrebuilt)
      .withPlatform(platformOpt)
      .withPreferPrebuilt(preferPrebuilt)
      .withOverrideProguardedBootstraps(proguarded)
}

object SharedInstallParams {

  lazy val defaultDir = InstallDir.defaultDir

  private[install] implicit def validationNelToCats[L, R](
    v: coursier.util.ValidationNel[L, R]
  ): ValidatedNel[L, R] =
    v.either match {
      case Left(h :: t) => Validated.invalid(NonEmptyList.of(h, t: _*))
      case Right(r)     => Validated.validNel(r)
    }

  def apply(options: SharedInstallOptions): SharedInstallParams = {

    val dir = options.installDir.filter(_.nonEmpty) match {
      case Some(d) => Paths.get(d)
      case None    => defaultDir
    }

    val graalvmVersion = GraalvmParams.resolveGraalVmOptions(options.graalvmDefaultVersion)

    val graalvmParams = GraalvmParams(
      graalvmVersion,
      options.graalvmOption
    )

    val onlyPrebuilt = options.onlyPrebuilt

    val platformOpt = options.installPlatform.orElse(Platform.get())

    val preferPrebuilt = options.installPreferPrebuilt

    SharedInstallParams(
      dir,
      Some(graalvmParams),
      onlyPrebuilt,
      platformOpt,
      preferPrebuilt,
      options.proguarded
    )
  }
}
