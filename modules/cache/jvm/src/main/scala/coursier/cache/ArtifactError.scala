package coursier.cache

import java.io.File

sealed abstract class ArtifactError(
  val `type`: String,
  val message: String,
  parentOpt: Option[Throwable]
) extends Exception(s"${`type`}: $message", parentOpt.orNull) {

  def this(`type`: String, message: String) =
    this(`type`, message, None)

  def describe: String = getMessage

  final def notFound: Boolean = this match {
    case _: ArtifactError.NotFound => true
    case _                         => false
  }

  final def forbidden: Boolean = this match {
    case _: ArtifactError.Forbidden => true
    case _                          => false
  }
}

object ArtifactError {

  // format: off
  final class DownloadError(val reason: String, e: Option[Throwable]) extends ArtifactError(
    "download error",
    reason,
    e
  )
  // format: on


  // format: off
  final class NotFound(
    val file: String,
    val permanent: Option[Boolean] = None
  ) extends ArtifactError(
    "not found",
    file
  )
  // format: on

  // format: off
  final class Forbidden(
    val file: String
  ) extends ArtifactError(
    "forbidden",
    file
  )
  // format: on

  // format: off
  final class Unauthorized(
    val file: String,
    val realm: Option[String]
  ) extends ArtifactError(
    "unauthorized",
    file + realm.fold("")(" (" + _ + ")")
  )
  // format: on

  // format: off
  final class ChecksumNotFound(
    val sumType: String,
    val file: String
  ) extends ArtifactError(
    "checksum not found",
    file
  )
  // format: on

  // format: off
  final class ChecksumErrors(
    val errors: Seq[(String, String)]
  ) extends ArtifactError(
    "checksum errors",
    errors.map { case (k, v) => s"$k: $v" }.mkString(", ")
  )
  // format: on

  // format: off
  final class ChecksumFormatError(
    val sumType: String,
    val file: String
  ) extends ArtifactError(
    "checksum format error",
    file
  )
  // format: on

  // format: off
  final class WrongChecksum(
    val sumType: String,
    val got: String,
    val expected: String,
    val file: String,
    val sumFile: String
  ) extends ArtifactError(
    "wrong checksum",
    s"$file (expected $sumType $expected in $sumFile, got $got)"
  )
  // format: on

  // format: off
  final class WrongLength(
    val got: Long,
    val expected: Long,
    val file: String
  ) extends ArtifactError(
    "wrong length",
    s"$file (expected $expected B, got $got B)"
  )
  // format: on

  // format: off
  final class FileTooOldOrNotFound(
    val file: String
  ) extends ArtifactError(
    "file in cache not found or too old",
    file
  )
  // format: on

  // format: off
  final class ForbiddenChangingArtifact(val url: String) extends ArtifactError(
    "changing artifact found",
    url
  )
  // format: on

  sealed abstract class Recoverable(
    `type`: String,
    message: String,
    parentOpt: Option[Throwable]
  ) extends ArtifactError(`type`, message, parentOpt) {
    def this(`type`: String, message: String) =
      this(`type`, message, None)
  }
  // format: off
  final class Locked(val file: File) extends Recoverable(
    "locked",
    file.toString
  )
  final class ConcurrentDownload(val url: String) extends Recoverable(
    "concurrent download",
    url
  )
  // format: on

}
