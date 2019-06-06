package coursier.cli.publish.params

import cats.data.{Validated, ValidatedNel}
import cats.implicits._
import coursier.publish.checksum.ChecksumType
import coursier.cli.publish.options.ChecksumOptions

final case class ChecksumParams(
  checksumsOpt: Option[Seq[ChecksumType]]
)

object ChecksumParams {

  def apply(options: ChecksumOptions): ValidatedNel[String, ChecksumParams] = {

    val checksumsOptV =
      options.checksums match {
        case None =>
          Validated.validNel(None)
        case Some(list) =>
          list
            .flatMap(_.split(','))
            .map(_.trim)
            .filter(_.nonEmpty)
            .traverse { s =>
              Validated.fromEither(ChecksumType.parse(s))
                .toValidatedNel
            }
            .map(Some(_))
      }

    checksumsOptV.map { checksumsOpt =>
      ChecksumParams(
        checksumsOpt
      )
    }
  }
}
