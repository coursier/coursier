package coursier.publish.download

import java.time.Instant

import coursier.core.Authentication
import coursier.publish.download.logger.DownloadLogger
import coursier.util.Task

trait Download {
  def downloadIfExists(url: String, authentication: Option[Authentication], logger: DownloadLogger): Task[Option[(Option[Instant], Array[Byte])]]
}

object Download {

  sealed abstract class Error(val transient: Boolean, message: String, cause: Throwable = null) extends Exception(message, cause)

  object Error {
    final class HttpError(url: String, code: Int, headers: Map[String, Seq[String]], response: String) extends Error(transient = code / 100 == 5, s"$url: HTTP $code\n$response")
    final class Unauthorized(url: String, realm: Option[String]) extends Error(transient = false, s"Unauthorized ($url, ${realm.getOrElse("[no realm]")})")
    final class DownloadError(url: String, exception: Throwable) extends Error(transient = false, s"Download error for $url", exception)
    final class FileException(exception: Throwable) extends Error(transient = false, "I/O error", exception) // can some exceptions be transient?
  }

}
