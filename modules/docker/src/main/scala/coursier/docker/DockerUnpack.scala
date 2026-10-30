package coursier.docker

import coursier.cache.{ArchiveCache, ArtifactError, CacheLogger}
import coursier.util.{Artifact, Task}

import java.io.File

import scala.concurrent.Await
import scala.concurrent.duration.Duration

object DockerUnpack {
  def unpack(
    priviledgedArchiveCache: ArchiveCache[Task],
    layerArtifacts: Seq[Artifact]
  ): Seq[File] = {

    val cache  = priviledgedArchiveCache.cache
    val logger = cache.loggerOpt.getOrElse(CacheLogger.nop)

    // The archive cache may not use the same file cache as DockerPull.pull, so we check the
    // layer digests again here, before extracting them (as root)
    def unpackLayer(artifact: Artifact): Task[Either[ArtifactError, File]] =
      priviledgedArchiveCache.getIfExists(artifact, isSingleFile = false).flatMap {
        case Left(err)        => Task.point(Left(err))
        case Right(Some(dir)) => Task.point(Right(dir))
        case Right(None)      =>
          DockerUtil.blobDigest(artifact.url) match {
            case None =>
              Task.point(Left(new ArtifactError.DownloadError(
                s"Cannot get the digest of layer ${artifact.url} from its URL",
                None
              )))
            case Some(digest) =>
              cache.file(artifact).run.flatMap {
                case Left(err) => Task.point(Left(err))
                case Right(f)  =>
                  Task.delay(DockerUtil.checkDigest(f, digest))
                    .flatMap(_ => priviledgedArchiveCache.get(artifact))
              }
          }
      }

    val layerDirsOrErrors = {
      val task = logger.using(Task.gather.gather(layerArtifacts.map(unpackLayer)))
      Await.result(task.future()(cache.ec), Duration.Inf)
    }

    val layerDirErrors = layerDirsOrErrors.collect {
      case Left(err) => err
    }

    layerDirErrors match {
      case Seq()                   =>
      case Seq(first, others @ _*) =>
        val ex = new Exception(first)
        for (other <- others)
          ex.addSuppressed(other)
        throw ex
    }

    layerDirsOrErrors.collect {
      case Right(f) => f
    }
  }
}
