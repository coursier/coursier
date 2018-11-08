package coursier.cli.publish.params

import cats.data.{Validated, ValidatedNel}
import cats.implicits._
import coursier.cli.publish.checksum.ChecksumType
import coursier.cli.publish.options.ChecksumOptions

final case class ChecksumParams(
  checksums: Seq[ChecksumType]
)

object ChecksumParams {

  val defaultChecksums = Seq(ChecksumType.MD5, ChecksumType.SHA1)

  def apply(options: ChecksumOptions): ValidatedNel[String, ChecksumParams] = {

    val checksumsV =
      options.checksums match {
        case None =>
          Validated.validNel(defaultChecksums)
        case Some(list) =>
          list.flatMap(_.split(',')).map(_.trim).filter(_.nonEmpty).traverse { s =>
            Validated.fromEither(ChecksumType.parse(s))
              .toValidatedNel
          }
      }

    checksumsV.map { checksums =>
      ChecksumParams(
        checksums
      )
    }
  }
}
