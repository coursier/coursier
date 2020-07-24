package coursier.cli.install

import java.io.File

import coursier.cache.{ArtifactError, Cache}
import coursier.util.{Artifact, EitherT, Task}

import scala.concurrent.ExecutionContext

final class NoopCache extends Cache[Task] {
  def ec = ExecutionContext.global
  def fetch: Cache.Fetch[Task] =
    _ => EitherT(Task.point[Either[String, String]](Left("unexpected download attempt")))
  def file(artifact: Artifact): EitherT[Task, ArtifactError, File] =
    EitherT(Task.point[Either[ArtifactError, File]](Left(new ArtifactError.DownloadError("unexpected download attempt", None))))
}
