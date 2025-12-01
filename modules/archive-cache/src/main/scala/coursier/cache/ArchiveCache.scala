package coursier.cache

import coursier.paths.CachePath
import coursier.util.{Artifact, EitherT, Sync, Task}
import coursier.util.Monad.ops._
import dataclass._
import org.apache.tika.Tika

import java.io.{File, InputStream}
import java.math.BigInteger
import java.nio.file.{Files, Path, StandardCopyOption}
import java.security.MessageDigest
import java.util.zip.{GZIPInputStream, ZipException, ZipFile}

import scala.jdk.CollectionConverters._
import scala.util.Using

@data class ArchiveCache[F[_]](
  location: File,
  cache: Cache[F] = FileCache(),
  unArchiver: UnArchiver = UnArchiver.default(),
  @since("2.1.25")
  openStream: UnArchiver.OpenStream = UnArchiver.default(),
  /** Set this to a non-empty value, to extract archives in single-level sub-directories under the
    * passed directory
    *
    * When this is non-empty, a hash of the default destination for archives is computed. Then, the
    * archive is actually extracted under the directory named with the hash under the directory
    * passed via `shortPathDirectory`.
    *
    * For example, if `shortPathDirectory` is set to `Some(new File("C:/jvms"))`,
    * `https://github.com/graalvm/graalvm-ce-builds/releases/download/jdk-23.0.1/graalvm-community-jdk-23.0.1_windows-x64_bin.zip`
    * is extracted under a directory like `C:\jvms\5aa5d09c\`, rather than
    * `{location}\https\github.com\graalvm\graalvm-ce-builds\releases\download\jdk-23.0.1\graalvm-community-jdk-23.0.1_windows-x64_bin.zip\`.
    * The latter directory path is used to compute the hash used in the former one.
    */
  shortPathDirectory: Option[File] = None,
  /** Whether to check the integrity of downloaded archives or not
    *
    * This checks for the integrity of archives using their compression format checksums. Only
    * supported for GZIP and ZIP archives for now.
    *
    * If the integrity check fails, a new download is attempted.
    *
    * Default: true
    */
  integrityCheck: Option[Boolean] = None
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
    val finalDir = shortPathDirectory match {
      case Some(shortPathBase) =>
        val sha1 = {
          val bytes    = MessageDigest.getInstance("SHA-1").digest(dir.toString.getBytes)
          val baseSha1 = new BigInteger(1, bytes).toString(16)
          "0" * (40 - baseSha1.length) + baseSha1
        }
        new File(shortPathBase, sha1.take(8))
      case None => dir
    }
    // FIXME security
    (finalDir, subPaths)
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

  private def integrityCheck0(
    url: String,
    file: File
  ): Option[Boolean] = {
    def readAndDiscard(is: InputStream): Unit = {
      val buf  = Array.ofDim[Byte](64 * 1024)
      var read = 0
      while ({
        read = is.read(buf)
        read >= 0
      }) {}
    }
    ArchiveCache.archiveType(url).collect {
      case ArchiveType.Gzip | ArchiveType.Tgz =>
        // read uncompressed content fully to validate GZIP checksum
        var fis: InputStream = null
        try {
          fis = Files.newInputStream(file.toPath)
          val gzis = new GZIPInputStream(fis)
          try {
            readAndDiscard(gzis)
            true
          }
          catch {
            case _: ZipException =>
              false
          }
        }
        finally
          if (fis != null)
            fis.close()
      case ArchiveType.Zip =>
        // read all uncompressed entries fully to validate entries' checksums
        var zf: ZipFile = null
        try {
          zf = new ZipFile(file)
          try {
            for (ent <- zf.entries().asScala)
              readAndDiscard(zf.getInputStream(ent))
            true
          }
          catch {
            case _: ZipException =>
              false
          }
        }
        finally
          if (zf != null)
            zf.close()
    }
  }

  def get(artifact: Artifact): F[Either[ArtifactError, File]] = {
    val (dir0, subPaths) = localDir(artifact)
    val artifact0        = artifact.withUrl(artifact.url.takeWhile(_ != '!'))
    val download: F[Either[ArtifactError, File]] = {
      def doDownload: F[Either[ArtifactError, File]] = cache.file(artifact0).run
      if (integrityCheck.getOrElse(true)) {
        val eitherT = for {
          f <- EitherT(doDownload)
          invalid <- {
            val cacheLocationOpt = cache match {
              case fc: FileCache[F] => Some(fc.location)
              case _                => None
            }
            EitherT(
              S.delay[Either[ArtifactError, Boolean]] {
                Right {
                  cacheLocationOpt match {
                    case Some(cacheLocation) =>
                      val invalid0 = CacheLocks.withLockOr(cacheLocation, f)(
                        {
                          val integrityFile = FileCache.auxiliaryFile(f, "integrity").toPath
                          val hasValidIntegrityFile = Files.exists(integrityFile) && {
                            val lastModified          = Files.getLastModifiedTime(f.toPath)
                            val integrityLastModified = Files.getLastModifiedTime(integrityFile)
                            integrityLastModified.toMillis() >= lastModified.toMillis()
                          }
                          if (!hasValidIntegrityFile)
                            Files.deleteIfExists(integrityFile)
                          !hasValidIntegrityFile && {
                            val invalid0 = integrityCheck0(artifact0.url, f).contains(false)
                            if (invalid0) {
                              Files.delete(f.toPath)
                              // clear all but the lock file for Windows?
                              FileCache.clearAuxiliaryFiles(f)
                            }
                            else
                              Files.write(integrityFile, Array.emptyByteArray)
                            invalid0
                          }
                        },
                        None
                      )
                      invalid0
                    case None =>
                      val invalid0 = integrityCheck0(artifact0.url, f).contains(false)
                      if (invalid0)
                        Files.delete(f.toPath)
                      invalid0
                  }
                }
              }
            )
          }
          f0 <- EitherT[F, ArtifactError, File](
            if (invalid) doDownload
            else S.point(Right(f))
          )
        } yield f0
        eitherT.run
      }
      else
        doDownload
    }

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
