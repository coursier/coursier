package coursier.cli.publish.upload

import java.nio.file.{Files, Path}
import java.time.Instant

import coursier.core.Authentication
import coursier.util.Task

import scala.util.control.NonFatal

/**
  * Copies
  * @param base
  */
final case class FileUpload(base: Path) extends Upload {
  private val base0 = base.normalize()
  def exists(
    url: String,
    authentication: Option[Authentication],
    logger: Upload.Logger
  ): Task[Boolean] = {

    val p = base0.resolve(url).normalize()
    if (p.startsWith(base0))
      Task.delay {
        logger.checking(url)
        val res = try {
          Right(Files.exists(p))
        } catch {
          case NonFatal(e) =>
            Left(e)
        }
        logger.checked(
          url,
          res.right.toOption.getOrElse(false),
          res.left.toOption.map(e => new Upload.Error.FileException(e))
        )

        Task.fromEither(res)
      }.flatMap(identity)
    else
      Task.fail(new Exception(s"Invalid path: $url (base: $base0, p: $p)"))
  }
  def downloadIfExists(
    url: String,
    authentication: Option[Authentication],
    logger: Upload.Logger
  ): Task[Option[(Option[Instant], Array[Byte])]] = {

    val p = base0.resolve(url).normalize()
    if (p.startsWith(base0))
      Task.delay {
        logger.downloadingIfExists(url)
        val res = try {
          if (Files.isRegularFile(p)) {
            val lastModified = Files.getLastModifiedTime(p).toInstant
            Right(Some((Some(lastModified), Files.readAllBytes(p))))
          } else
            Right(None)
        } catch {
          case NonFatal(e) =>
            Left(e)
        }
        logger.downloadedIfExists(
          url,
          res.right.toOption.flatMap(_.map(_._2.length)),
          res.left.toOption.map(e => new Upload.Error.FileException(e))
        )

        Task.fromEither(res)
      }.flatMap(identity)
    else
      Task.fail(new Exception(s"Invalid path: $url (base: $base0, p: $p)"))
  }
  def upload(
    url: String,
    authentication: Option[Authentication],
    content: Array[Byte],
    logger: Upload.Logger
  ): Task[Option[Upload.Error]] = {

    val p = base0.resolve(url).normalize()
    if (p.startsWith(base0))
      Task.delay {
        logger.uploading(url)
        val errorOpt = try {
          Files.createDirectories(p.getParent)
          Files.write(p, content)
          None
        } catch {
          case NonFatal(e) =>
            Some(e)
        }
        logger.uploaded(url, errorOpt.map(e => new Upload.Error.FileException(e)))

        None
      }
    else
      Task.fail(new Exception(s"Invalid path: $url (base: $base0, p: $p)"))
  }
}
