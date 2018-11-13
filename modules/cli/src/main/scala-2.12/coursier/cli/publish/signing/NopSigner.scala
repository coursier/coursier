package coursier.cli.publish.signing

import java.time.Instant

import coursier.cli.publish.Content
import coursier.cli.publish.fileset.FileSet
import coursier.util.Task

object NopSigner extends Signer {
  def sign(content: Content): Task[Either[String, String]] =
    Task.point(Right(""))

  override def signatures(
    fileSet: FileSet,
    now: Instant,
    dontSignExtensions: Set[String],
    dontSignFiles: Set[String],
    logger: SignerLogger
  ): Task[Either[(FileSet.Path, Content, String), FileSet]] =
    Task.point(Right(FileSet.empty))
}
