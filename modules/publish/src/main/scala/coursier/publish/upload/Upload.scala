package coursier.publish.upload

import coursier.publish.Content
import coursier.publish.fileset.{FileSet, Path}
import coursier.core.Authentication
import coursier.maven.MavenRepository
import coursier.publish.upload.logger.UploadLogger
import coursier.util.Task

/**
  * Uploads / sends content to a repository.
  */
trait Upload {

  // TODO Support chunked content?

  /**
    * Uploads content at the passed `url`.
    *
    * @param url: URL to upload content at
    * @param authentication: optional authentication parameters
    * @param content: content to upload
    * @param logger
    * @return an optional [[Upload.Error]], non-empty in case of error
    */
  def upload(url: String, authentication: Option[Authentication], content: Array[Byte], logger: UploadLogger, loggingIdOpt: Option[Object]): Task[Option[Upload.Error]]

  final def upload(url: String, authentication: Option[Authentication], content: Array[Byte], logger: UploadLogger): Task[Option[Upload.Error]] =
    upload(url, authentication, content, logger, None)

  /**
    * Uploads a whole [[FileSet]].
    *
    * @param repository
    * @param fileSet
    * @param logger
    * @return
    */
  final def uploadFileSet(
    repository: MavenRepository,
    fileSet: FileSet,
    logger: UploadLogger,
    parallel: Boolean
  ): Task[Seq[(Path, Content, Upload.Error)]] = {

    val baseUrl0 = repository.root.stripSuffix("/")

    // TODO Add exponential back off for transient errors

    // uploading stuff sequentially for now
    // stops at first error
    def doUpload(id: Object): Task[Seq[(Path, Content, Upload.Error)]] = {

      val tasks = fileSet
        .elements
        .map {
          case (path, content) =>
            val url = s"$baseUrl0/${path.elements.mkString("/")}"
            content.contentTask.flatMap(b =>
              upload(url, repository.authentication, b, logger, Some(id)).map(_.map((path, content, _)))
            )
        }

      if (parallel)
        Task.gather.gather(tasks).map { l =>
          l.flatten
        }
      else
        tasks
          .foldLeft(Task.point(Option.empty[(Path, Content, Upload.Error)])) {
            case (acc, task) =>
              for {
                previousErrorOpt <- acc
                errorOpt <- previousErrorOpt.fold(task)(e => Task.point(Some(e)))
              } yield errorOpt
          }
          .map(_.toSeq)
    }

    val before = Task.delay {
      val id = new Object
      logger.start()
      logger.uploadingSet(id, fileSet)
      id
    }

    def after(id: Object) = Task.delay {
      logger.uploadedSet(id, fileSet)
      logger.stop()
    }

    for {
      id <- before
      a <- doUpload(id).attempt
      _ <- after(id)
      res <- Task.fromEither(a)
    } yield res
  }
}

object Upload {

  sealed abstract class Error(val transient: Boolean, message: String, cause: Throwable = null) extends Exception(message, cause)

  object Error {
    final class HttpError(code: Int, headers: Map[String, Seq[String]], response: String) extends Error(transient = code / 100 == 5, s"HTTP $code\n$response")
    final class Unauthorized(url: String, realm: Option[String]) extends Error(transient = false, s"Unauthorized ($url, ${realm.getOrElse("[no realm]")})")
    final class UploadError(url: String, exception: Throwable) extends Error(transient = false, s"Error uploading $url", exception)
    final class FileException(exception: Throwable) extends Error(transient = false, "I/O error", exception) // can some exceptions be transient?
  }

}
