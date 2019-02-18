package coursier.cache

import java.io.{Serializable => _, _}
import java.math.BigInteger
import java.net.{HttpURLConnection, URLConnection}
import java.nio.charset.StandardCharsets.UTF_8
import java.nio.file.{Files, StandardCopyOption}
import java.security.MessageDigest
import java.util.concurrent.ExecutorService

import coursier.cache.internal.FileUtil
import coursier.core.{Artifact, Authentication, Repository}
import coursier.paths.CachePath
import coursier.util.{EitherT, Schedulable, Task}

import scala.annotation.tailrec
import scala.concurrent.ExecutionContext
import scala.concurrent.duration.Duration
import scala.util.control.NonFatal

final case class FileCache[F[_]](
  location: File = CacheDefaults.location,
  cachePolicies: Seq[CachePolicy] = CachePolicy.default,
  checksums: Seq[Option[String]] = CacheDefaults.checksums,
  logger: CacheLogger = CacheLogger.nop,
  pool: ExecutorService = CacheDefaults.pool,
  ttl: Option[Duration] = CacheDefaults.ttl,
  localArtifactsShouldBeCached: Boolean = false,
  followHttpToHttpsRedirections: Boolean = false,
  sslRetry: Int = CacheDefaults.sslRetryCount,
  retry: Int = CacheDefaults.defaultRetryCount,
  bufferSize: Int = CacheDefaults.bufferSize,
  S: Schedulable[F] = Task.schedulable
) extends Cache[F] {

  def localFile(url: String, user: Option[String] = None): File =
    FileCache.localFile0(url, location, user, localArtifactsShouldBeCached)

  private implicit val S0 = S

  import FileCache.{readFullyTo, partialContentResponseCode, invalidPartialContentResponseCode, contentLength}

  override def loggerOpt: Some[CacheLogger] =
    Some(logger)

  private def download(
    artifact: Artifact,
    checksums: Set[String],
    cachePolicy: CachePolicy
  ): F[Seq[((File, String), Either[ArtifactError, Unit])]] = {

    // Reference file - if it exists, and we get not found errors on some URLs, we assume
    // we can keep track of these missing, and not try to get them again later.
    val referenceFileOpt = artifact
      .extra
      .get("metadata")
      .map(a => localFile(a.url, a.authentication.map(_.user)))

    def referenceFileExists: Boolean = referenceFileOpt.exists(_.exists())

    def fileLastModified(file: File): EitherT[F, ArtifactError, Option[Long]] =
      EitherT {
        S.schedule(pool) {
          Right {
            val lastModified = file.lastModified()
            if (lastModified > 0L)
              Some(lastModified)
            else
              None
          } : Either[ArtifactError, Option[Long]]
        }
      }

    def urlLastModified(
      url: String,
      currentLastModifiedOpt: Option[Long], // for the logger
      logger: CacheLogger
    ): EitherT[F, ArtifactError, Option[Long]] =
      EitherT {
        S.schedule(pool) {
          var conn: URLConnection = null

          try {
            conn = CacheUrl.urlConnection(url, artifact.authentication)

            conn match {
              case c: HttpURLConnection =>
                logger.checkingUpdates(url, currentLastModifiedOpt)

                var success = false
                try {
                  c.setRequestMethod("HEAD")
                  val remoteLastModified = c.getLastModified

                  val res =
                    if (remoteLastModified > 0L)
                      Some(remoteLastModified)
                    else
                      None

                  success = true
                  logger.checkingUpdatesResult(url, currentLastModifiedOpt, res)

                  Right(res)
                } finally {
                  if (!success)
                    logger.checkingUpdatesResult(url, currentLastModifiedOpt, None)
                }

              case other =>
                Left(
                  ArtifactError.DownloadError(s"Cannot do HEAD request with connection $other ($url)"))
            }
          } finally {
            if (conn != null)
              CacheUrl.closeConn(conn)
          }
        }
      }

    def fileExists(file: File): F[Boolean] =
      S.schedule(pool) {
        file.exists()
      }

    def ttlFile(file: File): File =
      new File(file.getParent, s".${file.getName}.checked")

    def lastCheck(file: File): F[Option[Long]] = {

      val ttlFile0 = ttlFile(file)

      S.schedule(pool) {
        if (ttlFile0.exists())
          Some(ttlFile0.lastModified()).filter(_ > 0L)
        else
          None
      }
    }

    /** Not wrapped in a `Task` !!! */
    def doTouchCheckFile(file: File): Unit = {
      val ts = System.currentTimeMillis()
      val f = ttlFile(file)
      if (f.exists())
        f.setLastModified(ts)
      else {
        val fos = new FileOutputStream(f)
        fos.write(Array.empty[Byte])
        fos.close()
      }
    }

    def shouldDownload(file: File, url: String): EitherT[F, ArtifactError, Boolean] = {

      def checkNeeded = ttl.fold(S.point(true)) { ttl =>
        if (ttl.isFinite)
          S.bind(lastCheck(file)) {
            case None => S.point(true)
            case Some(ts) =>
              S.map(S.schedule(pool)(System.currentTimeMillis()))(_ > ts + ttl.toMillis)
          }
        else
          S.point(false)
      }

      def check = for {
        fileLastModOpt <- fileLastModified(file)
        urlLastModOpt <- urlLastModified(url, fileLastModOpt, logger)
      } yield {
        val fromDatesOpt = for {
          fileLastMod <- fileLastModOpt
          urlLastMod <- urlLastModOpt
        } yield fileLastMod < urlLastMod

        fromDatesOpt.getOrElse(true)
      }

      EitherT {
        S.bind(fileExists(file)) {
          case false =>
            S.point(Right(true))
          case true =>
            S.bind(checkNeeded) {
              case false =>
                S.point(Right(false))
              case true =>
                S.bind(check.run) {
                  case Right(false) =>
                    S.schedule(pool) {
                      doTouchCheckFile(file)
                      Right(false)
                    }
                  case other =>
                    S.point(other)
                }
            }
        }
      }
    }

    def remote(
      file: File,
      url: String
    ): EitherT[F, ArtifactError, Unit] =
      EitherT {
        S.schedule(pool) {

          val tmp = CachePath.temporaryFile(file)

          var lenOpt = Option.empty[Option[Long]]

          def doDownload(): Either[ArtifactError, Unit] =
            FileCache.downloading(url, file, sslRetry) {

              val alreadyDownloaded = tmp.length()

              var conn: URLConnection = null

              try {
                conn = CacheUrl.urlConnection(url, artifact.authentication)

                val partialDownload = conn match {
                  case conn0: HttpURLConnection if alreadyDownloaded > 0L =>
                    conn0.setRequestProperty("Range", s"bytes=$alreadyDownloaded-")

                    ((conn0.getResponseCode == partialContentResponseCode)
                       || (conn0.getResponseCode == invalidPartialContentResponseCode)) && {
                      val ackRange = Option(conn0.getHeaderField("Content-Range")).getOrElse("")

                      ackRange.startsWith(s"bytes $alreadyDownloaded-") || {
                        // unrecognized Content-Range header -> start a new connection with no resume
                        CacheUrl.closeConn(conn)
                        conn = CacheUrl.urlConnection(url, artifact.authentication)
                        false
                      }
                    }
                  case _ => false
                }

                val respCodeOpt = CacheUrl.responseCode(conn)

                if (followHttpToHttpsRedirections && url.startsWith("http://") && respCodeOpt.exists(c => c == 301 || c == 307 || c == 308))
                  conn match {
                    case conn0: HttpURLConnection =>
                      Option(conn0.getHeaderField("Location")) match {
                        case Some(loc) if loc.startsWith("https://") =>
                          CacheUrl.closeConn(conn)
                          conn = CacheUrl.urlConnection(loc, None) // not keeping authentication here… should we?
                        case _ =>
                          // ignored
                      }
                    case _ =>
                      // ignored
                  }

                if (respCodeOpt.contains(404))
                  Left(ArtifactError.NotFound(url, permanent = Some(true)))
                else if (respCodeOpt.contains(401))
                  Left(ArtifactError.Unauthorized(url, realm = CacheUrl.realm(conn)))
                else {
                  for (len0 <- Option(conn.getContentLengthLong) if len0 >= 0L) {
                    val len = len0 + (if (partialDownload) alreadyDownloaded else 0L)
                    logger.downloadLength(url, len, alreadyDownloaded, watching = false)
                  }

                  val in = new BufferedInputStream(conn.getInputStream, bufferSize)

                  val result =
                    try {
                      val out = CacheLocks.withStructureLock(location) {
                        tmp.getParentFile.mkdirs()
                        new FileOutputStream(tmp, partialDownload)
                      }
                      try readFullyTo(in, out, logger, url, if (partialDownload) alreadyDownloaded else 0L, bufferSize)
                      finally out.close()
                    } finally in.close()

                  CacheLocks.withStructureLock(location) {
                    file.getParentFile.mkdirs()
                    Files.move(tmp.toPath, file.toPath, StandardCopyOption.ATOMIC_MOVE)
                  }

                  for (lastModified <- Option(conn.getLastModified) if lastModified > 0L)
                    file.setLastModified(lastModified)

                  doTouchCheckFile(file)

                  Right(result)
                }
              } finally {
                if (conn != null)
                  CacheUrl.closeConn(conn)
              }
            }

          def checkDownload(): Option[Either[ArtifactError, Unit]] = {

            def progress(currentLen: Long): Unit =
              if (lenOpt.isEmpty) {
                lenOpt = Some(contentLength(url, artifact.authentication, logger).right.toOption.flatten)
                for (o <- lenOpt; len <- o)
                  logger.downloadLength(url, len, currentLen, watching = true)
              } else
                logger.downloadProgress(url, currentLen)

            def done(): Unit =
              if (lenOpt.isEmpty) {
                lenOpt = Some(contentLength(url, artifact.authentication, logger).right.toOption.flatten)
                for (o <- lenOpt; len <- o)
                  logger.downloadLength(url, len, len, watching = true)
              } else
                for (o <- lenOpt; len <- o)
                  logger.downloadProgress(url, len)

            if (file.exists()) {
              done()
              Some(Right(()))
            } else {
              // yes, Thread.sleep. 'tis our thread pool anyway.
              // (And the various resources make it not straightforward to switch to a more Task-based internal API here.)
              Thread.sleep(20L)

              val currentLen = tmp.length()

              if (currentLen == 0L && file.exists()) { // check again if file exists in case it was created in the mean time
                done()
                Some(Right(()))
              } else {
                progress(currentLen)
                None
              }
            }
          }

          logger.downloadingArtifact(url)

          var res: Either[ArtifactError, Unit] = null

          try {
            res = CacheLocks.withLockOr(location, file)(
              doDownload(),
              checkDownload()
            )
          } finally {
            logger.downloadedArtifact(url, success = res != null && res.isRight)
          }

          res
        }
      }

    def errFile(file: File) = new File(file.getParentFile, "." + file.getName + ".error")

    def remoteKeepErrors(file: File, url: String): EitherT[F, ArtifactError, Unit] = {

      val errFile0 = errFile(file)

      def validErrFileExists =
        EitherT {
          S.schedule[Either[ArtifactError, Boolean]](pool) {
            Right(referenceFileExists && errFile0.exists())
          }
        }

      def createErrFile =
        EitherT {
          S.schedule[Either[ArtifactError, Unit]](pool) {
            if (referenceFileExists) {
              if (!errFile0.exists())
                Files.write(errFile0.toPath, Array.emptyByteArray)
            }

            Right(())
          }
        }

      def deleteErrFile =
        EitherT {
          S.schedule[Either[ArtifactError, Unit]](pool) {
            if (errFile0.exists())
              errFile0.delete()

            Right(())
          }
        }

      def retainError =
        EitherT {
          S.bind(remote(file, url).run) {
            case err @ Left(ArtifactError.NotFound(_, Some(true))) =>
              S.map(createErrFile.run)(_ => err: Either[ArtifactError, Unit])
            case other =>
              S.map(deleteErrFile.run)(_ => other)
          }
        }

      cachePolicy match {
        case CachePolicy.FetchMissing | CachePolicy.LocalOnly | CachePolicy.LocalUpdate | CachePolicy.LocalUpdateChanging =>
          validErrFileExists.flatMap { exists =>
            if (exists)
              EitherT(
                S.point[Either[ArtifactError, Unit]](Left(ArtifactError.NotFound(url, Some(true)))))
            else
              retainError
          }

        case CachePolicy.ForceDownload | CachePolicy.Update | CachePolicy.UpdateChanging =>
          retainError
      }
    }

    def checkFileExists(file: File, url: String,
                        log: Boolean = true): EitherT[F, ArtifactError, Unit] =
      EitherT {
        S.schedule(pool) {
          if (file.exists()) {
            logger.foundLocally(url)
            Right(())
          } else
            Left(ArtifactError.NotFound(file.toString))
        }
      }

    val urls =
      artifact.url +: {
        checksums
          .toSeq
          .flatMap(artifact.checksumUrls.get)
      }

    val cachePolicy0 = cachePolicy match {
      case CachePolicy.UpdateChanging if !artifact.changing =>
        CachePolicy.FetchMissing
      case CachePolicy.LocalUpdateChanging if !artifact.changing =>
        CachePolicy.LocalOnly
      case other =>
        other
    }

    val tasks =
      for (url <- urls) yield {
        val file = localFile(url, artifact.authentication.map(_.user))

        val res =
          if (url.startsWith("file:/") && !localArtifactsShouldBeCached) {
            // for debug purposes, flaky with URL-encoded chars anyway
            // def filtered(s: String) =
            //   s.stripPrefix("file:/").stripPrefix("//").stripSuffix("/")
            // assert(
            //   filtered(url) == filtered(file.toURI.toString),
            //   s"URL: ${filtered(url)}, file: ${filtered(file.toURI.toString)}"
            // )
            checkFileExists(file, url)
          } else {
            def update = shouldDownload(file, url).flatMap {
              case true =>
                remoteKeepErrors(file, url)
              case false =>
                EitherT(S.point[Either[ArtifactError, Unit]](Right(())))
            }

            cachePolicy0 match {
              case CachePolicy.LocalOnly =>
                checkFileExists(file, url)
              case CachePolicy.LocalUpdateChanging | CachePolicy.LocalUpdate =>
                checkFileExists(file, url, log = false).flatMap { _ =>
                  update
                }
              case CachePolicy.UpdateChanging | CachePolicy.Update =>
                update
              case CachePolicy.FetchMissing =>
                checkFileExists(file, url) orElse remoteKeepErrors(file, url)
              case CachePolicy.ForceDownload =>
                remoteKeepErrors(file, url)
            }
          }

        S.map(res.run)((file, url) -> _)
      }

    S.gather(tasks)
  }

  def validateChecksum(
    artifact: Artifact,
                       sumType: String
  ): EitherT[F, ArtifactError, Unit] = {

    val localFile0 = localFile(artifact.url, artifact.authentication.map(_.user))

    EitherT {
      artifact.checksumUrls.get(sumType) match {
        case Some(sumUrl) =>
          val sumFile = localFile(sumUrl, artifact.authentication.map(_.user))

          S.schedule(pool) {
            val sumOpt = CacheChecksum.parseRawChecksum(Files.readAllBytes(sumFile.toPath))

            sumOpt match {
              case None =>
                Left(ArtifactError.ChecksumFormatError(sumType, sumFile.getPath))

              case Some(sum) =>
                val md = MessageDigest.getInstance(sumType)

                val is = new FileInputStream(localFile0)
                try FileUtil.withContent(is, md.update(_, 0, _))
                finally is.close()

                val digest = md.digest()
                val calculatedSum = new BigInteger(1, digest)

                if (sum == calculatedSum)
                  Right(())
                else
                  Left(ArtifactError.WrongChecksum(
                    sumType,
                    calculatedSum.toString(16),
                    sum.toString(16),
                    localFile0.getPath,
                    sumFile.getPath
                  ))
            }
          }

        case None =>
          S.point[Either[ArtifactError, Unit]](Left(ArtifactError.ChecksumNotFound(sumType, localFile0.getPath)))
      }
    }
  }

  private val checksums0 = if (checksums.isEmpty) Seq(None) else checksums

  private def filePerPolicy(
    artifact: Artifact,
    policy: CachePolicy,
    retry: Int = retry
  ): EitherT[F, ArtifactError, File] =
    EitherT {
      S.map(download(
        artifact,
        checksums = checksums0.collect { case Some(c) => c }.toSet,
        cachePolicy = policy
      )) { results =>
        val checksum = checksums0.find {
          case None => true
          case Some(c) =>
            artifact.checksumUrls.get(c).exists { cUrl =>
              results.exists { case ((_, u), b) =>
                u == cUrl && b.isRight
              }
            }
        }

        val ((f, _), res) = results.head
        res.right.flatMap { _ =>
          checksum match {
            case None =>
              // FIXME All the checksums should be in the error, possibly with their URLs
              //       from artifact.checksumUrls
              Left(ArtifactError.ChecksumNotFound(checksums0.last.get, ""))
            case Some(c) => Right((f, c))
          }
        }
      }
    }.flatMap {
      case (f, None) => EitherT(S.point[Either[ArtifactError, File]](Right(f)))
      case (f, Some(c)) =>
        validateChecksum(artifact, c).map(_ => f)
    }.leftFlatMap {
      case err: ArtifactError.WrongChecksum =>
        if (retry <= 0) {
          EitherT(S.point(Left(err)))
        }
        else {
          EitherT {
            S.schedule[Either[ArtifactError, Unit]](pool) {
              val badFile = localFile(artifact.url, artifact.authentication.map(_.user))
              badFile.delete()
              logger.removedCorruptFile(artifact.url, Some(err.describe))
              Right(())
            }
          }.flatMap { _ =>
            filePerPolicy(artifact, policy, retry - 1)
          }
        }
      case err =>
        EitherT(S.point(Left(err)))
    }

  def file(artifact: Artifact): EitherT[F, ArtifactError, File] =
    file(artifact, retry)

  def file(artifact: Artifact, retry: Int): EitherT[F, ArtifactError, File] =
    (filePerPolicy(artifact, cachePolicies.head, retry) /: cachePolicies.tail.map(filePerPolicy(artifact, _, retry)))(_ orElse _)

  private def fetchPerPolicy(artifact: Artifact, policy: CachePolicy): EitherT[F, String, String] =
    filePerPolicy(artifact, policy).leftMap(_.describe).flatMap { f =>

      def notFound(f: File) = Left(s"${f.getCanonicalPath} not found")

      def read(f: File) =
        try Right(new String(Files.readAllBytes(f.toPath), UTF_8))
        catch {
          case NonFatal(e) =>
            Left(s"Could not read (file:${f.getCanonicalPath}): ${e.getMessage}")
        }

      val res = if (f.exists()) {
        if (f.isDirectory) {
          if (artifact.url.startsWith("file:")) {

            val elements = f.listFiles().map { c =>
              val name = c.getName
              val name0 = if (c.isDirectory)
                name + "/"
              else
                name

              s"""<li><a href="$name0">$name0</a></li>"""
            }.mkString

            val page =
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

            Right(page)
          } else {
            val f0 = new File(f, ".directory")

            if (f0.exists()) {
              if (f0.isDirectory)
                Left(s"Woops: ${f.getCanonicalPath} is a directory")
              else
                read(f0)
            } else
              notFound(f0)
          }
        } else
          read(f)
      } else
        notFound(f)

      EitherT(S.point[Either[String, String]](res))
    }

  def fetch: Repository.Fetch[F] =
    a =>
      (fetchPerPolicy(a, cachePolicies.head) /: cachePolicies.tail)(_ orElse fetchPerPolicy(a, _))

  def fetchs: Seq[Repository.Fetch[F]] =
    cachePolicies.map { p =>
      (a: Artifact) =>
        fetchPerPolicy(a, p)
    }

  lazy val ec = ExecutionContext.fromExecutorService(pool)

}

object FileCache {

  private[coursier] def localFile0(url: String, cache: File, user: Option[String], localArtifactsShouldBeCached: Boolean): File =
    CachePath.localFile(url, cache, user.orNull, localArtifactsShouldBeCached)

  private def readFullyTo(
    in: InputStream,
    out: OutputStream,
    logger: CacheLogger,
    url: String,
    alreadyDownloaded: Long,
    bufferSize: Int
  ): Unit = {

    val b = Array.fill[Byte](bufferSize)(0)

    @tailrec
    def helper(count: Long): Unit = {
      val read = in.read(b)
      if (read >= 0) {
        out.write(b, 0, read)
        out.flush()
        logger.downloadProgress(url, count + read)
        helper(count + read)
      }
    }

    helper(alreadyDownloaded)
  }


  private def downloading[T](
    url: String,
    file: File,
    sslRetry: Int
  )(
    f: => Either[ArtifactError, T]
  ): Either[ArtifactError, T] = {

    @tailrec
    def helper(retry: Int): Either[ArtifactError, T] = {

      val resOpt =
        try {
          val res0 = CacheLocks.withUrlLock(url) {
            try f
            catch {
              case nfe: FileNotFoundException if nfe.getMessage != null =>
                Left(ArtifactError.NotFound(nfe.getMessage))
            }
          }

          val res = res0.getOrElse {
            Left(ArtifactError.ConcurrentDownload(url))
          }

          Some(res)
        }
        catch {
          case _: javax.net.ssl.SSLException if retry >= 1 =>
            // TODO If Cache is made an (instantiated) class at some point, allow to log that exception.
            None
          case NonFatal(e) =>
            Some(Left(
              ArtifactError.DownloadError(
                s"Caught $e${Option(e.getMessage).fold("")(" (" + _ + ")")} while downloading $url"
              )
            ))
        }

      resOpt match {
        case Some(res) => res
        case None =>
          helper(retry - 1)
      }
    }

    helper(sslRetry)
  }

  private val partialContentResponseCode = 206
  private val invalidPartialContentResponseCode = 416

  private def contentLength(
    url: String,
    authentication: Option[Authentication],
    logger: CacheLogger
  ): Either[ArtifactError, Option[Long]] = {

    var conn: URLConnection = null

    try {
      conn = CacheUrl.urlConnection(url, authentication)

      conn match {
        case c: HttpURLConnection =>
          logger.gettingLength(url)

          var success = false
          try {
            c.setRequestMethod("HEAD")
            val len = Some(c.getContentLengthLong)
              .filter(_ >= 0L)

            success = true
            logger.gettingLengthResult(url, len)

            Right(len)
          } finally {
            if (!success)
              logger.gettingLengthResult(url, None)
          }

        case other =>
          Left(ArtifactError.DownloadError(s"Cannot do HEAD request with connection $other ($url)"))
      }
    } finally {
      if (conn != null)
        CacheUrl.closeConn(conn)
    }
  }


  def create[F[_]](
    cache: File = CacheDefaults.location,
    cachePolicies: Seq[CachePolicy] = CachePolicy.default,
    checksums: Seq[Option[String]] = CacheDefaults.checksums,
    logger: CacheLogger = CacheLogger.nop,
    pool: ExecutorService = CacheDefaults.pool,
    ttl: Option[Duration] = CacheDefaults.ttl,
    localArtifactsShouldBeCached: Boolean = false,
    followHttpToHttpsRedirections: Boolean = false,
    sslRetry: Int = CacheDefaults.sslRetryCount,
    retry: Int = CacheDefaults.defaultRetryCount,
    bufferSize: Int = CacheDefaults.bufferSize
  )(implicit S: Schedulable[F] = Task.schedulable): FileCache[F] =
    FileCache(
      location = cache,
      cachePolicies = cachePolicies,
      checksums = checksums,
      logger = logger,
      pool = pool,
      ttl = ttl,
      localArtifactsShouldBeCached = localArtifactsShouldBeCached,
      followHttpToHttpsRedirections = followHttpToHttpsRedirections,
      sslRetry = sslRetry,
      retry = retry,
      bufferSize = bufferSize,
      Schedulable[F]
    )

}
