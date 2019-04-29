package coursier.cache

import java.io.File

// TODO Make that extend Exception
sealed abstract class ArtifactError(
  val `type`: String,
  val message: String
) extends Product with Serializable {
  def describe: String = s"${`type`}: $message"

  final def notFound: Boolean = this match {
    case _: ArtifactError.NotFound => true
    case _ => false
  }
}

object ArtifactError {

  final case class DownloadError(reason: String) extends ArtifactError(
    "download error",
    reason
  )

  final case class NotFound(
    file: String,
    permanent: Option[Boolean] = None
  ) extends ArtifactError(
    "not found",
    file
  )

  final case class Unauthorized(
    file: String,
    realm: Option[String]
  ) extends ArtifactError(
    "unauthorized",
    file + realm.fold("")(" (" + _ + ")")
  )

  final case class ChecksumNotFound(
    sumType: String,
    file: String
  ) extends ArtifactError(
    "checksum not found",
    file
  )

  final case class ChecksumErrors(
    errors: Seq[(String, String)]
  ) extends ArtifactError(
    "checksum errors",
    errors.map { case (k, v) => s"$k: $v" }.mkString(", ")
  )

  final case class ChecksumFormatError(
    sumType: String,
    file: String
  ) extends ArtifactError(
    "checksum format error",
    file
  )

  final case class WrongChecksum(
    sumType: String,
    got: String,
    expected: String,
    file: String,
    sumFile: String
  ) extends ArtifactError(
    "wrong checksum",
    s"$file (expected $sumType $expected in $sumFile, got $got)"
  )

  final case class FileTooOldOrNotFound(
    file: String
  ) extends ArtifactError(
    "file in cache not found or too old",
    file
  )

  sealed abstract class Recoverable(
    `type`: String,
    message: String
  ) extends ArtifactError(`type`, message)
  final case class Locked(file: File) extends Recoverable(
    "locked",
    file.toString
  )
  final case class ConcurrentDownload(url: String) extends Recoverable(
    "concurrent download",
    url
  )

}
