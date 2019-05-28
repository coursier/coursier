package coursier.publish.signing

import java.time.Instant

import coursier.publish.Content
import coursier.publish.fileset.{FileSet, Path}
import coursier.publish.signing.logger.SignerLogger
import coursier.util.Task

object NopSigner extends Signer {
  def sign(content: Content): Task[Either[String, String]] =
    Task.point(Right(""))

  override def signatures(
    fileSet: FileSet,
    now: Instant,
    dontSignExtensions: Set[String],
    dontSignFiles: Set[String],
    logger: => SignerLogger
  ): Task[Either[(Path, Content, String), FileSet]] =
    Task.point(Right(FileSet.empty))
}
