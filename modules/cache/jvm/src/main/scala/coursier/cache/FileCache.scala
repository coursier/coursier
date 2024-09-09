package coursier.cache

import java.io.{Serializable => _, _}
import java.math.BigInteger
import java.nio.charset.StandardCharsets.UTF_8
import java.nio.file.{FileAlreadyExistsException, Files, NoSuchFileException, StandardCopyOption}
import java.security.MessageDigest
import java.time.Clock
import java.util.Locale
import java.util.concurrent.ExecutorService
import javax.net.ssl.{HostnameVerifier, SSLSocketFactory}

import coursier.cache.internal.{Downloader, DownloadResult, FileUtil}
import coursier.credentials.{Credentials, DirectCredentials, FileCredentials}
import coursier.paths.CachePath
import coursier.util.{Artifact, EitherT, Sync, Task, WebPage}
import coursier.util.Monad.ops._
import dataclass.data

import scala.concurrent.ExecutionContext
import scala.concurrent.duration.{Duration, FiniteDuration}
import scala.util.control.NonFatal

// format: off
@data class FileCache[F[_]](
  location: File,
  cachePolicies: Seq[CachePolicy] = CacheDefaults.cachePolicies,
  checksums: Seq[Option[String]] = CacheDefaults.checksums,
  credentials: Seq[Credentials] = CacheDefaults.credentials,
  logger: CacheLogger = CacheLogger.nop,
  pool: ExecutorService = CacheDefaults.pool,
  ttl: Option[Duration] = CacheDefaults.ttl,
  localArtifactsShouldBeCached: Boolean = false,
  followHttpToHttpsRedirections: Boolean = true,
  followHttpsToHttpRedirections: Boolean = false,
  maxRedirections: Option[Int] = CacheDefaults.maxRedirections,
  @deprecated("Unused, use retry instead", "2.1.11")
    sslRetry: Int = CacheDefaults.retryCount,
  sslSocketFactoryOpt: Option[SSLSocketFactory] = None,
  hostnameVerifierOpt: Option[HostnameVerifier] = None,
  retry: Int = CacheDefaults.retryCount,
  bufferSize: Int = CacheDefaults.bufferSize,
  @since("2.0.16")
    classLoaders: Seq[ClassLoader] = Nil,
  @since("2.1.0-RC3")
    clock: Clock = Clock.systemDefaultZone(),
  @since("2.1.11")
    retryBackoffInitialDelay: FiniteDuration = CacheDefaults.retryBackoffInitialDelay,
  @since("2.1.11")
    retryBackoffMultiplier: Double = CacheDefaults.retryBackoffMultiplier
)(implicit
  sync: Sync[F]
) extends Cache[F] {
  // format: on

  private def S = sync

  private lazy val allCredentials0 =
    credentials.flatMap(_.get())

  def allCredentials: F[Seq[DirectCredentials]] =
    S.delay(allCredentials0)

  def withLocation(location: String): FileCache[F] =
    withLocation(new File(location))
  def noCredentials: FileCache[F] =
    withCredentials(Nil)
  def addCredentials(credentials: Credentials*): FileCache[F] =
    withCredentials(this.credentials ++ credentials)
  def addFileCredentials(credentialFile: File): FileCache[F] =
    withCredentials(this.credentials :+ FileCredentials(credentialFile.getAbsolutePath))
  def withTtl(ttl: Duration): FileCache[F] =
    withTtl(Some(ttl))
  def withSslSocketFactory(sslSocketFactory: SSLSocketFactory): FileCache[F] =
    withSslSocketFactoryOpt(Some(sslSocketFactory))
  def withHostnameVerifier(hostnameVerifier: HostnameVerifier): FileCache[F] =
    withHostnameVerifierOpt(Some(hostnameVerifier))
  def withMaxRedirections(max: Int): FileCache[F] =
    withMaxRedirections(Some(max))

  def localFile(url: String, user: Option[String] = None): File =
    FileCache.localFile0(url, location, user, localArtifactsShouldBeCached)

  import FileCache.{auxiliaryFile, clearAuxiliaryFiles}

  override def loggerOpt: Some[CacheLogger] =
    Some(logger)

  private val checksums0      = if (checksums.isEmpty) Seq(None) else checksums
  private val actualChecksums = checksums0.flatMap(_.toSeq).distinct

  private def download(
    artifact: Artifact,
    cachePolicy: CachePolicy
  ): F[Seq[DownloadResult]] =
    Downloader(
      artifact,
      cachePolicy,
      location,
      actualChecksums,
      allCredentials,
      logger,
      pool,
      ttl,
      localArtifactsShouldBeCached,
      followHttpToHttpsRedirections,
      followHttpsToHttpRedirections,
      maxRedirections,
      retry,
      sslSocketFactoryOpt,
      hostnameVerifierOpt,
      bufferSize,
      classLoaders,
      clock
    ).download

  def validateChecksum(
    artifact: Artifact,
    sumType: String
  ): EitherT[F, ArtifactError, Unit] = {

    val localFile0 = localFile(artifact.url, artifact.authentication.map(_.user))

    val headerSumFile = Seq(auxiliaryFile(localFile0, sumType))
    val downloadedSumFile = artifact.checksumUrls.get(sumType).map { sumUrl =>
      localFile(sumUrl, artifact.authentication.map(_.user))
    }

    EitherT {
      S.schedule(pool) {
        (headerSumFile ++ downloadedSumFile.toSeq).find(_.exists()) match {
          case Some(sumFile) =>
            val sumOpt = CacheChecksum.parseRawChecksum(Files.readAllBytes(sumFile.toPath))

            sumOpt match {
              case None =>
                Left(new ArtifactError.ChecksumFormatError(sumType, sumFile.getPath))

              case Some(sum) =>
                val calculatedSum: BigInteger =
                  FileCache.persistedDigest(location, sumType, localFile0)

                if (sum == calculatedSum)
                  Right(())
                else
                  Left(new ArtifactError.WrongChecksum(
                    sumType,
                    calculatedSum.toString(16),
                    sum.toString(16),
                    localFile0.getPath,
                    sumFile.getPath
                  ))
            }

          case None =>
            val err = new ArtifactError.ChecksumNotFound(sumType, localFile0.getPath)
            Left(err): Either[ArtifactError, Unit]
        }
      }
    }
  }

  private def filePerPolicy(
    artifact: Artifact,
    policy: CachePolicy,
    retry: Int = retry
  ): EitherT[F, ArtifactError, File] = {

    val artifact0 = allCredentials.map { allCredentials =>
      if (artifact.authentication.isEmpty) {
        val authOpt = allCredentials
          .find(_.autoMatches(artifact.url, None))
          .map(_.authentication)
        artifact.withAuthentication(authOpt)
      }
      else
        artifact
    }

    EitherT[F, ArtifactError, Artifact](artifact0.map(Right(_)))
      .flatMap { a =>
        filePerPolicy0(a, policy, retry)
      }
  }

  private def filePerPolicy0(
    artifact: Artifact,
    policy: CachePolicy,
    retry: Int = retry
  ): EitherT[F, ArtifactError, File] =
    EitherT {
      download(
        artifact,
        cachePolicy = policy
      ).map { results =>
        val resultsMap = results
          .map {
            case res => res.url -> res.errorOpt
          }
          .toMap

        val checksumResults = checksums0.map {
          case None => None
          case Some(c) =>
            val url = artifact.checksumUrls.getOrElse(
              c,
              s"${artifact.url}.${c.toLowerCase(Locale.ROOT).filter(_ != '-')}"
            )
            Some((c, url, resultsMap.get(url)))
        }
        val checksum = checksumResults.collectFirst {
          case None => None
          case Some((c, _, Some(errorOpt))) if errorOpt.isEmpty =>
            Some(c)
        }
        def checksumErrors: Seq[(String, String)] = checksumResults.collect {
          case Some((c, url, None)) =>
            // shouldn't happen, the download method must have returned results for this…
            c -> s"$url not downloaded"
          case Some((c, _, Some(Some(e)))) =>
            c -> e.describe
        }

        val res = results.head
        res.errorOpt.toLeft(()).flatMap { _ =>
          checksum match {
            case None =>
              // FIXME All the checksums should be in the error, possibly with their URLs
              //       from artifact0.checksumUrls
              Left(new ArtifactError.ChecksumErrors(checksumErrors))
            case Some(c) => Right((res.file, c))
          }
        }
      }
    }.flatMap {
      case (f, None) => EitherT(S.point[Either[ArtifactError, File]](Right(f)))
      case (f, Some(c)) =>
        validateChecksum(artifact, c).map(_ => f)
    }.leftFlatMap {
      case err: ArtifactError.WrongChecksum =>
        val badFile         = localFile(artifact.url, artifact.authentication.map(_.user))
        val badChecksumFile = new File(err.sumFile)
        val foundBadFileInCache = {
          val location0 = location.getCanonicalPath.stripSuffix(File.separator) + File.separator
          badFile.getCanonicalPath.startsWith(location0) &&
          badChecksumFile.getCanonicalPath.startsWith(location0)
        }
        if (retry <= 0 || !foundBadFileInCache)
          EitherT(S.point(Left(err)))
        else
          EitherT {
            S.schedule[Either[ArtifactError, Unit]](pool) {
              assert(foundBadFileInCache)
              badFile.delete()
              badChecksumFile.delete()
              clearAuxiliaryFiles(badFile)
              logger.removedCorruptFile(artifact.url, Some(err.describe))
              Right(())
            }
          }.flatMap { _ =>
            filePerPolicy0(artifact, policy, retry - 1)
          }
      case err: ArtifactError.ChecksumNotFound =>
        if (retry <= 0)
          EitherT(S.point(Left(err)))
        else
          filePerPolicy0(artifact, policy, retry - 1)
      case err =>
        EitherT(S.point(Left(err)))
    }

  def file(artifact: Artifact): EitherT[F, ArtifactError, File] =
    file(artifact, retry)

  def file(artifact: Artifact, retry: Int): EitherT[F, ArtifactError, File] =
    cachePolicies.tail.map(filePerPolicy(artifact, _, retry))
      .foldLeft(filePerPolicy(artifact, cachePolicies.head, retry))(_ orElse _)

  private def fetchPerPolicy(
    artifact: Artifact,
    policy: CachePolicy
  ): EitherT[F, String, String] = {

    val (artifact0, links) =
      if (artifact.url.endsWith("/.links"))
        (artifact.withUrl(artifact.url.stripSuffix(".links")), true)
      else (artifact, false)

    filePerPolicy(artifact0, policy).leftMap(_.describe).flatMap { f =>

      def notFound(f: File) = Left(s"${f.getCanonicalPath} not found")

      def read(f: File) =
        try {
          val content =
            if (links) {
              val linkFile = auxiliaryFile(f, "links")
              if (f.getName == ".directory" && linkFile.isFile)
                new String(Files.readAllBytes(linkFile.toPath), UTF_8)
              else
                WebPage.listElements(artifact0.url, new String(Files.readAllBytes(f.toPath), UTF_8))
                  .mkString("\n")
            }
            else
              new String(Files.readAllBytes(f.toPath), UTF_8)
          Right(content)
        }
        catch {
          case NonFatal(e) =>
            Left(s"Could not read (file:${f.getCanonicalPath}): ${e.getMessage}")
        }

      val res =
        if (f.exists())
          if (f.isDirectory)
            if (artifact0.url.startsWith("file:")) {

              val content =
                if (links)
                  f.listFiles()
                    .map { c =>
                      val name = c.getName
                      if (c.isDirectory)
                        name + "/"
                      else
                        name
                    }
                    .sorted
                    .mkString("\n")
                else {

                  val elements = f.listFiles()
                    .map { c =>
                      val name = c.getName
                      if (c.isDirectory)
                        name + "/"
                      else
                        name
                    }
                    .sorted
                    .map { name0 =>
                      s"""<li><a href="$name0">$name0</a></li>"""
                    }
                    .mkString

                  s"""<!DOCTYPE html>
                     |<html>
                     |<head></head>
                     |<body>
                     |<ul>
                     |$elements
                     |</ul>
                     |</body>
                     |</html>
                 """.stripMargin
                }

              Right(content)
            }
            else {
              val f0 = new File(f, ".directory")

              if (f0.exists())
                if (f0.isDirectory)
                  Left(s"Woops: ${f.getCanonicalPath} is a directory")
                else
                  read(f0)
              else
                notFound(f0)
            }
          else
            read(f)
        else
          notFound(f)

      EitherT(S.point[Either[String, String]](res))
    }
  }

  def fetch: Cache.Fetch[F] =
    a =>
      cachePolicies.tail
        .foldLeft(fetchPerPolicy(a, cachePolicies.head))(_ orElse fetchPerPolicy(a, _))

  override def fetchs: Seq[Cache.Fetch[F]] =
    // format: off
    cachePolicies.map { p =>
      (a: Artifact) =>
        fetchPerPolicy(a, p)
    }
    // format: on

  lazy val ec = ExecutionContext.fromExecutorService(pool)

}

object FileCache {

  private[coursier] def localFile0(
    url: String,
    cache: File,
    user: Option[String],
    localArtifactsShouldBeCached: Boolean
  ): File =
    CachePath.localFile(url, cache, user.orNull, localArtifactsShouldBeCached)

  private def auxiliaryFilePrefix(file: File): String =
    s".${file.getName}__"

  private[cache] def clearAuxiliaryFiles(file: File): Unit = {
    val prefix = auxiliaryFilePrefix(file)
    val filter: FilenameFilter = new FilenameFilter {
      def accept(dir: File, name: String): Boolean =
        name.startsWith(prefix)
    }
    for (f <- file.getParentFile.listFiles(filter))
      f.delete() // check return type?
  }

  private[coursier] def auxiliaryFile(file: File, key: String): File = {
    val key0 = key.toLowerCase(Locale.ROOT).filter(_ != '-')
    new File(file.getParentFile, s"${auxiliaryFilePrefix(file)}$key0")
  }

  def apply[F[_]]()(implicit S: Sync[F] = Task.sync): FileCache[F] =
    FileCache(CacheDefaults.location)(S)

  /* Store computed cache in a file so we don't have to recompute them over and over. */
  private def persistedDigest(location: File, sumType: String, localFile: File): BigInteger = {
    // only store computed files within coursier cache folder
    val isInCache: Boolean = {
      val location0 = location.getCanonicalPath.stripSuffix(File.separator) + File.separator
      localFile.getCanonicalPath.startsWith(location0)
    }

    val digested: Array[Byte] =
      if (!isInCache) computeDigest(sumType, localFile)
      else {
        val cacheFile     = auxiliaryFile(localFile, sumType + ".computed")
        val cacheFilePath = cacheFile.toPath

        try Files.readAllBytes(cacheFilePath)
        catch {
          case _: NoSuchFileException =>
            val bytes: Array[Byte] = computeDigest(sumType, localFile)

            // Atomically write file by using a temp file in the same directory
            val tmpFile =
              File.createTempFile(cacheFile.getName, ".tmp", cacheFile.getParentFile).toPath
            try {
              Files.write(tmpFile, bytes)
              try Files.move(tmpFile, cacheFilePath, StandardCopyOption.ATOMIC_MOVE)
              catch {
                // In the case of multiple processes/threads which all compute this digest, first thread wins.
                case _: FileAlreadyExistsException => ()
              }
            }
            finally Files.deleteIfExists(tmpFile)

            bytes
        }
      }

    new BigInteger(1, digested)
  }

  private def computeDigest(sumType: String, localFile: File): Array[Byte] = {
    val md = MessageDigest.getInstance(sumType)

    var is: FileInputStream = null
    try {
      is = new FileInputStream(localFile)
      FileUtil.withContent(is, new FileUtil.UpdateDigest(md))
    }
    finally if (is != null) is.close()

    md.digest()
  }
}
