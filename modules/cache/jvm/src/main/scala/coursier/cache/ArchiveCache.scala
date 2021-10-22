package coursier.cache

import coursier.util.Artifact
import coursier.util.Monad.ops._
import dataclass._

import coursier.util.Sync
import coursier.paths.CachePath
import coursier.util.Task

import java.io.File
import java.nio.file.attribute.FileTime
import java.nio.file.{Files, StandardCopyOption}

@data class ArchiveCache[F[_]](
  location: File,
  cache: Cache[F] = FileCache(),
  unArchiver: UnArchiver = UnArchiver.default()
)(implicit
  sync: Sync[F]
) {

  private def S = sync

  private def localDir(artifact: Artifact): File =
    CachePath.localFile(
      artifact.url,
      location,
      artifact.authentication.map(_.user).orNull,
      false
    )

  def getIfExists(artifact: Artifact): F[Either[ArtifactError, Option[File]]] = {

    val dir          = localDir(artifact)
    val archiveType0 = ArchiveCache.archiveType(artifact.url)

    val dirTask: F[Either[ArtifactError, Option[File]]] =
      S.delay(dir.exists()).map {
        case true  => Right(Some(dir))
        case false => Right(None)
      }

    if (archiveType0.singleFile)
      dirTask.flatMap {
        case Right(Some(dir)) =>
          S.delay {
            dir.listFiles() match {
              case Array(f) => Right(Some(f))
              case _        => Right(Some(dir)) // throw instead?
            }
          }
        case other => S.point(other)
      }
    else
      dirTask
  }

  def get(artifact: Artifact): F[Either[ArtifactError, File]] = {

    val dir          = localDir(artifact)
    val archiveType0 = ArchiveCache.archiveType(artifact.url)

    def extract(f: File, deleteDest: Boolean): F[Either[ArtifactError, File]] =
      S.delay {
        CacheLocks.withLockOr(location, dir)(
          if (deleteDest || !dir.exists()) {
            val tmp = CachePath.temporaryFile(dir)
            ArchiveCache.deleteRecursive(tmp)
            Files.createDirectories(tmp.toPath)
            unArchiver.extract(archiveType0, f, tmp, overwrite = false)
            val lastModifiedTime = Files.getLastModifiedTime(f.toPath)
            Files.setLastModifiedTime(tmp.toPath, lastModifiedTime)
            def moveToDest(): Unit =
              Files.move(tmp.toPath, dir.toPath, StandardCopyOption.ATOMIC_MOVE)
            if (dir.exists())
              if (deleteDest) {
                ArchiveCache.deleteRecursive(dir)
                moveToDest()
              }
              else
                // We shouldn't go in that branch thanks to the lock and the condition above
                ArchiveCache.deleteRecursive(tmp)
            else
              moveToDest()
          }, {
            Thread.sleep(50L)
            None
          }
        )

        Right(dir)
      }

    val downloadAndExtract: F[Either[ArtifactError, File]] =
      cache.loggerOpt.getOrElse(CacheLogger.nop).using(cache.file(artifact).run).flatMap {
        case Left(err) => S.point(Left(err))
        case Right(f)  => extract(f, deleteDest = false)
      }

    val dirTask: F[Either[ArtifactError, File]] =
      S.delay(dir.exists()).flatMap {
        case true =>
          if (artifact.changing)
            cache.loggerOpt.getOrElse(CacheLogger.nop).using(cache.file(artifact).run).flatMap {
              case Left(err) => S.point(Left(err))
              case Right(f) =>
                val archiveLastModifiedTime = Files.getLastModifiedTime(f.toPath)
                val dirLastModifiedTime     = Files.getLastModifiedTime(dir.toPath)
                if (archiveLastModifiedTime == dirLastModifiedTime)
                  S.point(Right(dir))
                else
                  extract(f, deleteDest = true)
            }
          else
            S.point(Right(dir))
        case false =>
          downloadAndExtract
      }

    if (archiveType0.singleFile)
      dirTask.flatMap {
        case Right(dir) =>
          S.delay {
            dir.listFiles() match {
              case Array(f) => Right(f)
              case _        => Right(dir) // throw instead?
            }
          }
        case other => S.point(other)
      }
    else
      dirTask
  }

}

object ArchiveCache {

  def apply[F[_]]()(implicit S: Sync[F] = Task.sync): ArchiveCache[F] =
    ArchiveCache(CacheDefaults.archiveCacheLocation)(S)

  private def deleteRecursive(f: File): Unit = {
    if (f.isDirectory)
      f.listFiles().foreach(deleteRecursive)
    f.delete()
  }

  private def archiveType(url: String): ArchiveType =
    // TODO Case-insensitive comparisons?
    if (url.endsWith(".tar.gz") || url.endsWith(".tgz"))
      ArchiveType.Tgz
    else if (url.endsWith(".zip"))
      ArchiveType.Zip
    else if (url.endsWith(".gz"))
      ArchiveType.Gzip
    else
      sys.error(s"Unrecognized archive type: $url")

}
