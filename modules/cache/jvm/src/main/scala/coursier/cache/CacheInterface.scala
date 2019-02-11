package coursier.cache
import java.io.File
import java.util.concurrent.ExecutorService

import coursier.FileError
import coursier.core.{Artifact, Repository}
import coursier.util.EitherT

abstract class CacheInterface[F[_]] {
  def file(artifact: Artifact): EitherT[F, FileError, File]
  def fetch: Repository.Fetch[F]
  def fetchs: Seq[Repository.Fetch[F]]

  def pool: ExecutorService
}
