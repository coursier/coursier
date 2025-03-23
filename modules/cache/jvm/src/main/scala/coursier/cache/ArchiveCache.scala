package coursier.cache

import coursier.paths.CachePath
import coursier.util.{Artifact, EitherT, Sync, Task}
import coursier.util.Monad.ops._
import dataclass._
import org.apache.tika.Tika

import java.io.File
import java.nio.file.{Files, Path, StandardCopyOption}

import scala.util.Using

@data class ArchiveCache[F[_]](
  location: File,
  cache: Cache[F] = FileCache(),
  unArchiver: UnArchiver = UnArchiver.default(),
  @since("2.1.25")
  openStream: UnArchiver.OpenStream = UnArchiver.default()
)(implicit
  sync: Sync[F]
) {

  private def S = sync

  private def localDir(artifact: Artifact): (File, Seq[String]) = {
    val Array(mainUrl, subPaths @ _*) = artifact.url.split("\\!", -1)
    val dir = CachePath.localFile(
      mainUrl,
      location,
      artifact.authentication.flatMap(_.userOpt).orNull,
      true
    )
    // FIXME security
    (dir, subPaths)
  }

  def getIfExists(artifact: Artifact): F[Either[ArtifactError, Option[File]]] =
    ArchiveCache.archiveType(artifact.url) match {
      case Some(archiveType0) =>
        getIfExists(artifact, archiveType0.singleFile)
      case None =>
        S.point(
          Left(
            new ArtifactError.DownloadError(
              s"Cannot get archive format from URL for ${artifact.url}",
              None
            )
          )
        )
    }

  /** @param artifact
    * @param isSingleFile
    *   whether the corresponding archive can contain multiple files (like tar archive) or not
    *   (compressed single file)
    * @return
    */
  def getIfExists(
    artifact: Artifact,
    isSingleFile: Boolean
  ): F[Either[ArtifactError, Option[File]]] = {
    val (dir0, subPaths) = localDir(artifact)
    val dir = subPaths
      .foldLeft(dir0.toPath) { (dir, subPath) =>
        val f = dir.resolve(subPath)
        f.getParent.resolve("." + f.getFileName)
      }
      .toFile

    val dirTask: F[Either[ArtifactError, Option[File]]] =
      S.delay(dir.exists()).map {
        case true  => Right(Some(dir))
        case false => Right(None)
      }

    if (isSingleFile)
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

  private[cache] def get0(
    dir: File,
    urlOpt: Option[String],
    download: F[Either[ArtifactError, File]]
  ): F[Either[ArtifactError, File]] = {

    def maybeArchiveType(f: File, urlOpt: Option[String]): Either[ArtifactError, ArchiveType] =
      urlOpt.flatMap(ArchiveCache.archiveType).map(Right(_)).getOrElse {
        val tika = new Tika
        val mimeType = Using.resource(Files.newInputStream(f.toPath)) { is =>
          tika.detect(is)
        }
        ArchiveType.fromMimeType(mimeType) match {
          case Some(compressed: ArchiveType.Compressed) =>
            val t0 = Using.resource(Files.newInputStream(f.toPath)) { is =>
              val tika = new Tika
              tika.detect(openStream.inputStream(compressed, is))
            }
            Right {
              ArchiveType.fromMimeType(t0) match {
                case Some(ArchiveType.Tar) => compressed.tar
                case _                     => compressed
              }
            }
          case Some(tpe) =>
            Right(tpe)
          case None =>
            Left(
              new ArtifactError.DownloadError(
                s"Cannot detect archive type of $f, or unsupported archive type (MIME type $mimeType)",
                None
              )
            )
        }
      }

    def extract(
      f: File,
      deleteDest: Boolean,
      archiveType0: ArchiveType
    ): F[Either[ArtifactError, (File, ArchiveType)]] =
      S.delay {
        val archiveType1: ArchiveType = CacheLocks.withLockOr(location, dir)(
          {
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
            }
            archiveType0
          }, {
            Thread.sleep(50L)
            None
          }
        )

        Right((dir, archiveType1))
      }

    val dirTask: F[Either[ArtifactError, (File, ArchiveType)]] =
      S.delay(dir.exists()).flatMap { exists =>
        download.flatMap {
          case Left(err) => S.point(Left(err))
          case Right(f) =>
            maybeArchiveType(f, urlOpt) match {
              case Left(err) => S.point(Left(err))
              case Right(archiveType0) =>
                if (exists) {
                  val archiveLastModifiedTime = Files.getLastModifiedTime(f.toPath)
                  val dirLastModifiedTime     = Files.getLastModifiedTime(dir.toPath)
                  if (archiveLastModifiedTime == dirLastModifiedTime)
                    S.point(Right((dir, archiveType0)))
                  else
                    extract(f, deleteDest = true, archiveType0)
                }
                else
                  extract(f, deleteDest = false, archiveType0)
            }
        }
      }

    dirTask.flatMap {
      case Right((dir, arcType)) if arcType.singleFile =>
        S.delay {
          Right {
            dir.listFiles() match {
              case Array(f) => f
              case _        => dir // throw instead?
            }
          }
        }
      case other =>
        S.point(other.map(_._1))
    }
  }

  def get(artifact: Artifact): F[Either[ArtifactError, File]] = {
    val (dir0, subPaths) = localDir(artifact)
    val artifact0        = artifact.withUrl(artifact.url.takeWhile(_ != '!'))
    val download: F[Either[ArtifactError, File]] =
      cache.loggerOpt.getOrElse(CacheLogger.nop).using(cache.file(artifact0).run)

    val main = get0(
      dir0,
      Some(artifact0.url),
      download
    )

    EitherT(main).flatMap { dir =>

      def process(
        file: Path,
        url: String,
        subPaths0: List[String]
      ): EitherT[F, ArtifactError, Path] =
        subPaths0 match {
          case Nil => EitherT.point(file)
          case subPath :: rest =>
            val arc  = file.resolve(subPath)
            val dest = arc.getParent.resolve("." + arc.getFileName)
            val url0 = url + "!" + subPath
            EitherT(get0(dest.toFile, Some(url0), S.point(Right(arc.toFile)))).flatMap { dir0 =>
              process(dir0.toPath, url0, rest)
            }
        }

      if (subPaths.isEmpty) EitherT.point(dir)
      else {
        val init = subPaths.init
        val last = subPaths.last
        process(dir.toPath, artifact0.url, init.toList).map(_.resolve(last).toFile)
      }
    }.run
  }

}

object ArchiveCache {

  def apply[F[_]]()(implicit S: Sync[F] = Task.sync): ArchiveCache[F] =
    ArchiveCache(CacheDefaults.archiveCacheLocation)(S)

  def priviledged[F[_]]()(implicit S: Sync[F] = Task.sync): ArchiveCache[F] =
    ArchiveCache(CacheDefaults.priviledgedArchiveCacheLocation)(S)
      .withUnArchiver(UnArchiver.priviledged())

  private def deleteRecursive(f: File): Unit = {
    if (f.isDirectory)
      f.listFiles().foreach(deleteRecursive)
    f.delete()
  }

  private def archiveType(url: String): Option[ArchiveType] =
    // TODO Case-insensitive comparisons?
    if (url.endsWith(".tar"))
      Some(ArchiveType.Tar)
    else if (url.endsWith(".tar.gz") || url.endsWith(".tgz") || url.endsWith(".apk"))
      Some(ArchiveType.Tgz)
    else if (url.endsWith(".tar.bz2") || url.endsWith(".tbz2"))
      Some(ArchiveType.Tbz2)
    else if (url.endsWith(".tar.xz") || url.endsWith(".txz"))
      Some(ArchiveType.Txz)
    else if (url.endsWith(".tar.zst") || url.endsWith(".tzst"))
      Some(ArchiveType.Tzst)
    else if (url.endsWith(".zip") || url.endsWith(".jar"))
      Some(ArchiveType.Zip)
    else if (url.endsWith(".ar") || url.endsWith(".deb"))
      Some(ArchiveType.Ar)
    else if (url.endsWith(".gz"))
      Some(ArchiveType.Gzip)
    else if (url.endsWith(".xz"))
      Some(ArchiveType.Xz)
    else
      None

}
