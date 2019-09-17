package coursier.cli.publish.params

import java.nio.file.{Files, Path, Paths}

import cats.data.{NonEmptyList, Validated, ValidatedNel}
import cats.implicits._
import coursier.cli.publish.options.SinglePackageOptions
import coursier.core.{Classifier, Extension}

final case class SinglePackageParams(
  jarOpt: Option[Path],
  pomOpt: Option[Path],
  artifacts: Seq[(Classifier, Extension, Path)],
  `package`: Boolean
)

object SinglePackageParams {

  private def q = "\""

  def apply(options: SinglePackageOptions): ValidatedNel[String, SinglePackageParams] = {

    // FIXME This does some I/O (not reflected in return type)

    def fileExtensionV(path: String): ValidatedNel[String, (Path, String)] =
      fileV(path).withEither(_.right.flatMap { p =>
        val name = p.getFileName.toString
        val idx = name.lastIndexOf('.')
        if (idx < 0)
          Left(NonEmptyList.one(s"$path has no extension, specify one by passing it with classifier:extension:$path"))
        else {
          val ext = name.drop(idx + 1)
          if (ext.isEmpty)
            Left(NonEmptyList.one(s"$path extension is empty, specify one by passing it with classifier:extension:$path"))
          else
            Right((p, ext))
        }
      })

    def fileV(path: String): ValidatedNel[String, Path] = {
      val p = Paths.get(path)
      if (!Files.exists(p))
        Validated.invalidNel(s"not found: $path")
      else if (!Files.isRegularFile(p))
        Validated.invalidNel(s"not a regular file: $path")
      else
        Validated.validNel(p)
    }

    def fileOptV(pathOpt: Option[String]): ValidatedNel[String, Option[Path]] =
      pathOpt match {
        case None =>
          Validated.validNel(None)
        case Some(path) =>
          fileV(path).map(Some(_))
      }

    val jarOptV = fileOptV(options.jar)
    val pomOptV = fileOptV(options.pom)

    val artifactsV = options.artifact.traverse { s =>
      s.split(":", 3) match {
        case Array(strClassifier, strExtension, path) =>
          fileV(path).map((Classifier(strClassifier), Extension(strExtension), _))
        case Array(strClassifier, path) =>
          fileExtensionV(path).map {
            case (p, ext) =>
              (Classifier(strClassifier), Extension(ext), p)
          }
        case _ =>
          Validated.invalidNel(s"Malformed artifact argument: $s (expected: ${q}classifier:/path/to/artifact$q)")
      }
    }

    val packageV = options.`package` match {
      case Some(true) =>
        Validated.validNel(true)
      case Some(false) =>
        if (options.jar.nonEmpty || options.pom.nonEmpty || options.artifact.nonEmpty)
          Validated.invalidNel("Cannot specify --package=false along with --pom or --jar or --artifact")
        else
          Validated.validNel(false)
      case None =>
        val p = options.jar.nonEmpty || options.pom.nonEmpty || options.artifact.nonEmpty
        Validated.validNel(p)
    }

    (jarOptV, pomOptV, artifactsV, packageV).mapN {
      (jarOpt, pomOpt, artifacts, package0) =>
        SinglePackageParams(
          jarOpt,
          pomOpt,
          artifacts,
          package0
        )
    }
  }
}
