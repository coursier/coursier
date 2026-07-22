package coursier.docker

import coursier.cache.{ArchiveCache, CacheLogger}
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

    val layerDirsOrErrors = {
      val task = logger.using(Task.gather.gather(layerArtifacts.map(priviledgedArchiveCache.get)))
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
