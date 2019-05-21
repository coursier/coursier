package coursier.cli.install

import java.nio.file.{Path, Paths}
import java.util.concurrent.TimeUnit

import cats.data.ValidatedNel

import scala.concurrent.duration.Duration

final case class UpdateParams(
  shared: SharedInstallParams,
  dir: Path
)

object UpdateParams {
  def apply(options: UpdateOptions): ValidatedNel[String, UpdateParams] = {

    val sharedV = SharedInstallParams(options.sharedInstallOptions, Some(Duration(0L, TimeUnit.MILLISECONDS)))

    val dir = options.dir
      .map(Paths.get(_))
      .getOrElse(InstallParams.defaultDir)

    sharedV.map { shared =>
      UpdateParams(
        shared,
        dir
      )
    }
  }
}
