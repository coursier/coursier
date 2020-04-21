package coursier.cli.install

import java.nio.file.{Path, Paths}

import caseapp.Tag
import cats.data.{Validated, ValidatedNel}

final case class UninstallParams(
  dir: Path,
  all: Boolean,
  verbosity: Int
)

object UninstallParams {
  def apply(options: UninstallOptions): ValidatedNel[String, UninstallParams] = {

    val dir = options.installDir.filter(_.nonEmpty) match {
      case Some(d) => Paths.get(d)
      case None => SharedInstallParams.defaultDir
    }

    val all = options.all

    val verbosityV =
      if (Tag.unwrap(options.quiet) > 0 && Tag.unwrap(options.verbose) > 0)
        Validated.invalidNel("Cannot have both quiet, and verbosity > 0")
      else
        Validated.validNel(Tag.unwrap(options.verbose) - Tag.unwrap(options.quiet))

    verbosityV.map { verbosity =>
      UninstallParams(
        dir,
        all,
        verbosity
      )
    }
  }
}
