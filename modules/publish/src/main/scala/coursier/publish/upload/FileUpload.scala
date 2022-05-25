package coursier.publish.upload

import coursier.core.Authentication
import coursier.paths.Util
import coursier.publish.upload.logger.UploadLogger
import coursier.util.Task

import java.net.URI
import java.nio.file.{Files, Path, Paths}

import scala.util.control.NonFatal

/** Copies
  * @param base
  */
final case class FileUpload(base: Path) extends Upload {
  private val base0 = base.normalize()
  def upload(
    url: String,
    authentication: Option[Authentication],
    content: Array[Byte],
    logger: UploadLogger,
    loggingId: Option[Object]
  ): Task[Option[Upload.Error]] = {

    val p = base0.resolve(Paths.get(new URI(url))).normalize()
    if (p.startsWith(base0))
      Task.delay {
        logger.uploading(p.toString, loggingId, Some(content.length))
        val errorOpt =
          try {
            Util.createDirectories(p.getParent)
            Files.write(p, content)
            None
          }
          catch {
            case NonFatal(e) =>
              Some(e)
          }
        logger.uploaded(p.toString, loggingId, errorOpt.map(e => new Upload.Error.FileException(e)))

        None
      }
    else
      Task.fail(new Exception(s"Invalid path: $url (base: $base0, p: $p)"))
  }
}
