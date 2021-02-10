package coursier.cache.internal

import java.io.{Serializable => _, _}
import java.net.{HttpURLConnection, URLConnection}
import java.nio.charset.StandardCharsets.UTF_8
import java.nio.file.{Files, StandardCopyOption}
import java.util.Locale
import java.util.concurrent.ExecutorService
import javax.net.ssl.{HostnameVerifier, SSLSocketFactory}

import coursier.cache._
import coursier.core.Authentication
import coursier.credentials.DirectCredentials
import coursier.paths.{CachePath, Util}
import coursier.util.{Artifact, EitherT, Sync, Task, WebPage}
import coursier.util.Monad.ops._
import dataclass.data

import scala.annotation.tailrec
import scala.concurrent.duration.Duration
import scala.util.control.NonFatal

@data class Downloader[F[_]](
  artifact: Artifact,
  cachePolicy: CachePolicy,
  location: File,
  actualChecksums: Seq[String],
  allCredentials: F[Seq[DirectCredentials]],
  logger: CacheLogger = CacheLogger.nop,
  pool: ExecutorService = CacheDefaults.pool,
  ttl: Option[Duration] = CacheDefaults.ttl,
  localArtifactsShouldBeCached: Boolean = false,
  followHttpToHttpsRedirections: Boolean = true,
  followHttpsToHttpRedirections: Boolean = false,
  maxRedirections: Option[Int] = CacheDefaults.maxRedirections,
  sslRetry: Int = CacheDefaults.sslRetryCount,
  sslSocketFactoryOpt: Option[SSLSocketFactory] = None,
  hostnameVerifierOpt: Option[HostnameVerifier] = None,
  bufferSize: Int = CacheDefaults.bufferSize
)(implicit
  S: Sync[F]
) {

  private def localFile(url: String, user: Option[String] = None): File =
    FileCache.localFile0(url, location, user, localArtifactsShouldBeCached)

  // Reference file - if it exists, and we get not found errors on some URLs, we assume
  // we can keep track of these missing, and not try to get them again later.
  private lazy val referenceFileOpt = artifact.extra.get("metadata").map { a =>
    localFile(a.url, a.authentication.map(_.user))
  }

  private val cacheErrors = artifact.changing &&
    artifact.extra.contains("cache-errors")

  private def cacheErrors0: Boolean = cacheErrors || referenceFileOpt.exists(_.exists())

  private def fileLastModified(file: File): EitherT[F, ArtifactError, Option[Long]] =
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

  private def urlLastModified(
    url: String,
    currentLastModifiedOpt: Option[Long], // for the logger
    logger: CacheLogger
  ): EitherT[F, ArtifactError, Option[Long]] =
    EitherT(allCredentials.flatMap { allCredentials0 =>
      S.schedule(pool) {
        var conn: URLConnection = null

        try {
          conn = ConnectionBuilder(url)
            .withAuthentication(artifact.authentication)
            .withFollowHttpToHttpsRedirections(followHttpToHttpsRedirections)
            .withFollowHttpsToHttpRedirections(followHttpsToHttpRedirections)
            .withAutoCredentials(allCredentials0)
            .withSslSocketFactoryOpt(sslSocketFactoryOpt)
            .withHostnameVerifierOpt(hostnameVerifierOpt)
            .withMethod("HEAD")
            .withMaxRedirectionsOpt(maxRedirections)
            .connection()

          conn match {
            case c: HttpURLConnection =>
              logger.checkingUpdates(url, currentLastModifiedOpt)

              var success = false
              try {
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
                new ArtifactError.DownloadError(s"Cannot do HEAD request with connection $other ($url)", None)
              )
          }
        } catch {
          case NonFatal(e) =>
            val ex = new ArtifactError.DownloadError(
              s"Caught $e${Option(e.getMessage).fold("")(" (" + _ + ")")} while getting last modified time of $url",
              Some(e)
            )
            if (java.lang.Boolean.getBoolean("coursier.cache.throw-exceptions"))
              throw ex
            Left(ex)
        } finally {
          if (conn != null)
            CacheUrl.closeConn(conn)
        }
      }
    })

  private def fileExists(file: File): F[Boolean] =
    S.schedule(pool) {
      file.exists()
    }

  private def ttlFile(file: File): File =
    new File(file.getParent, s".${file.getName}.checked")

  private def lastCheck(file: File): F[Option[Long]] = {

    val ttlFile0 = ttlFile(file)

    S.schedule(pool) {
      if (ttlFile0.exists())
        Some(ttlFile0.lastModified()).filter(_ > 0L)
      else
        None
    }
  }

  /** Not wrapped in a `Task` !!! */
  private def doTouchCheckFile(file: File, url: String, updateLinks: Boolean): Unit = {
    val ts = System.currentTimeMillis()
    val f = ttlFile(file)
    if (f.exists())
      f.setLastModified(ts)
    else {
      val fos = new FileOutputStream(f)
      fos.write(Array.empty[Byte])
      fos.close()
    }

    if (updateLinks && file.getName == ".directory") {
      val linkFile = FileCache.auxiliaryFile(file, "links")

      val succeeded =
        try {
          val content = WebPage.listElements(url, new String(Files.readAllBytes(file.toPath), UTF_8))
            .mkString("\n")

          var fos: FileOutputStream = null
          try {
            fos = new FileOutputStream(linkFile)
            fos.write(content.getBytes(UTF_8))
          } finally {
            if (fos != null)
              fos.close()
          }
          true
        } catch {
          case NonFatal(_) =>
            false
        }

      if (!succeeded)
        Files.deleteIfExists(linkFile.toPath)
    }
  }

  private def shouldDownload(file: File, url: String, checkRemote: Boolean): EitherT[F, ArtifactError, Boolean] = {
    val errFile0 = errFile(file)

    def checkErrFile: EitherT[F, ArtifactError, Unit] =
      EitherT {
        S.schedule[Either[ArtifactError, Unit]](pool) {
          if (referenceFileOpt.exists(_.exists()) && errFile0.exists())
            Left(new ArtifactError.NotFound(url, Some(true)))
          else if (cacheErrors && errFile0.exists()) {
            val ts = errFile0.lastModified()
            val now = System.currentTimeMillis()
            if (ts > 0L && (ttl.exists(!_.isFinite) || now < ts + ttl.fold(0L)(_.toMillis)))
              Left(new ArtifactError.NotFound(url))
            else
              Right(())
          } else
            Right(())
        }
      }

    def checkNeeded = ttl.fold(S.point(true)) { ttl =>
      if (ttl.isFinite)
        lastCheck(file).flatMap {
          case None => S.point(true)
          case Some(ts) =>
            S.schedule(pool)(System.currentTimeMillis())
              .map(_ > ts + ttl.toMillis)
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

    checkErrFile.flatMap { _ =>
      EitherT {
        fileExists(file).flatMap {
          case false =>
            S.point(Right(true))
          case true =>
            checkNeeded.flatMap {
              case false =>
                S.point(Right(false))
              case true if !checkRemote =>
                S.point(Right(true))
              case true if checkRemote =>
                check.run.flatMap {
                  case Right(false) =>
                    S.schedule(pool) {
                      doTouchCheckFile(file, url, updateLinks = false)
                      Right(false)
                    }
                  case other =>
                    S.point(other)
                }
            }
        }
      }
    }
  }

  private def remote(
    file: File,
    url: String,
    keepHeaderChecksums: Boolean
  ): EitherT[F, ArtifactError, Unit] =
    EitherT(allCredentials.flatMap { allCredentials0 =>
      S.schedule(pool) {

        val tmp = CachePath.temporaryFile(file)

        var lenOpt = Option.empty[Option[Long]]

        def doDownload(): Either[ArtifactError, Unit] =
          Downloader.downloading(url, file, sslRetry) {

            val alreadyDownloaded = tmp.length()

            var conn: URLConnection = null

            val authenticationOpt =
              artifact.authentication match {
                case Some(auth) if auth.userOnly =>
                  allCredentials0
                    .find(_.matches(url, auth.user))
                    .map(_.authentication)
                    .orElse(artifact.authentication) // Default to None instead?
                case _ =>
                  artifact.authentication
              }

            try {
              val (conn0, partialDownload) = ConnectionBuilder(url)
                .withAuthentication(authenticationOpt)
                .withAlreadyDownloaded(alreadyDownloaded)
                .withFollowHttpToHttpsRedirections(followHttpToHttpsRedirections)
                .withFollowHttpsToHttpRedirections(followHttpsToHttpRedirections)
                .withAutoCredentials(allCredentials0.filter(_.matchHost)) // just in case
                .withSslSocketFactoryOpt(sslSocketFactoryOpt)
                .withHostnameVerifierOpt(hostnameVerifierOpt)
                .withMethod("GET")
                .withMaxRedirectionsOpt(maxRedirections)
                .connectionMaybePartial()
              conn = conn0

              val respCodeOpt = CacheUrl.responseCode(conn)

              if (respCodeOpt.contains(404))
                Left(new ArtifactError.NotFound(url, permanent = Some(true)))
              else if (respCodeOpt.contains(401))
                Left(new ArtifactError.Unauthorized(url, realm = CacheUrl.realm(conn)))
              else {
                for (len0 <- Option(conn.getContentLengthLong) if len0 >= 0L) {
                  val len = len0 + (if (partialDownload) alreadyDownloaded else 0L)
                  logger.downloadLength(url, len, alreadyDownloaded, watching = false)
                }

                val auxiliaryData: Map[String, Array[Byte]] =
                  if (keepHeaderChecksums)
                    conn match {
                      case conn0: HttpURLConnection =>
                        Downloader.checksumHeader.flatMap { c =>
                          Option(conn0.getHeaderField(s"X-Checksum-$c")) match {
                            case Some(str) =>
                              Some(c -> str.getBytes(UTF_8))
                            case None =>
                              None
                          }
                        }.toMap
                      case _ =>
                        Map()
                    }
                  else
                    Map()

                val lastModifiedOpt = Option(conn.getLastModified).filter(_ > 0L)

                val in = new BufferedInputStream(conn.getInputStream, bufferSize)

                val result =
                  try {
                    val out = CacheLocks.withStructureLock(location) {
                      Util.createDirectories(tmp.toPath.getParent);
                      new FileOutputStream(tmp, partialDownload)
                    }
                    try Downloader.readFullyTo(in, out, logger, url, if (partialDownload) alreadyDownloaded else 0L, bufferSize)
                    finally out.close()
                  } finally in.close()

                FileCache.clearAuxiliaryFiles(file)

                for ((key, data) <- auxiliaryData) {
                  val dest = FileCache.auxiliaryFile(file, key)
                  val tmpDest = CachePath.temporaryFile(dest)
                  Util.createDirectories(tmpDest.toPath.getParent)
                  Files.write(tmpDest.toPath, data)
                  for (lastModified <- lastModifiedOpt)
                    tmpDest.setLastModified(lastModified)
                  Util.createDirectories(dest.toPath.getParent)
                  Files.move(tmpDest.toPath, dest.toPath, StandardCopyOption.ATOMIC_MOVE)
                }

                CacheLocks.withStructureLock(location) {
                  Util.createDirectories(file.toPath.getParent)
                  Files.move(tmp.toPath, file.toPath, StandardCopyOption.ATOMIC_MOVE)
                }

                for (lastModified <- lastModifiedOpt)
                  file.setLastModified(lastModified)

                doTouchCheckFile(file, url, updateLinks = true)

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
              lenOpt = Some(
                Downloader.contentLength(
                  url,
                  artifact.authentication,
                  followHttpToHttpsRedirections,
                  followHttpsToHttpRedirections,
                  allCredentials0,
                  sslSocketFactoryOpt,
                  hostnameVerifierOpt,
                  logger,
                  maxRedirections
                ).toOption.flatten
              )
              for (o <- lenOpt; len <- o)
                logger.downloadLength(url, len, currentLen, watching = true)
            } else
              logger.downloadProgress(url, currentLen)

          def done(): Unit =
            if (lenOpt.isEmpty) {
              lenOpt = Some(
                Downloader.contentLength(
                  url,
                  artifact.authentication,
                  followHttpToHttpsRedirections,
                  followHttpsToHttpRedirections,
                  allCredentials0,
                  sslSocketFactoryOpt,
                  hostnameVerifierOpt,
                  logger,
                  maxRedirections
                ).toOption.flatten
              )
              for (o <- lenOpt; len <- o)
                logger.downloadLength(url, len, len, watching = true)
            } else
              for (o <- lenOpt; len <- o)
                logger.downloadProgress(url, len)

          if (file.exists()) {
            done()
            val res = lenOpt.flatten match {
              case None =>
                Right(())
              case Some(len) =>
                val fileLen = file.length()
                if (len == fileLen)
                  Right(())
                else
                  Left(new ArtifactError.WrongLength(fileLen, len, file.getAbsolutePath))
            }
            Some(res)
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
    })

  private def errFile(file: File) = new File(file.getParentFile, "." + file.getName + ".error")

  private def remoteKeepErrors(file: File, url: String, keepHeaderChecksums: Boolean): EitherT[F, ArtifactError, Unit] = {

    val errFile0 = errFile(file)

    def createErrFile =
      EitherT {
        S.schedule[Either[ArtifactError, Unit]](pool) {
          if (cacheErrors0) {
            val p = errFile0.toPath
            Util.createDirectories(p.getParent)
            Files.write(p, Array.emptyByteArray)
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

    EitherT {
      remote(file, url, keepHeaderChecksums).run.flatMap {
        case err @ Left(nf: ArtifactError.NotFound) if nf.permanent.contains(true) =>
          createErrFile.run.map(_ => err: Either[ArtifactError, Unit])
        case other =>
          deleteErrFile.run.map(_ => other)
      }
    }
  }

  private def checkFileExists(
    file: File,
    url: String,
    log: Boolean = true
  ): EitherT[F, ArtifactError, Unit] =
    EitherT {
      S.schedule(pool) {
        if (file.exists()) {
          logger.foundLocally(url)
          Right(())
        } else
          Left(new ArtifactError.NotFound(file.toString))
      }
    }

  private val cachePolicy0 = cachePolicy match {
    case CachePolicy.UpdateChanging if !artifact.changing =>
      CachePolicy.FetchMissing
    case CachePolicy.LocalUpdateChanging | CachePolicy.LocalOnlyIfValid if !artifact.changing =>
      CachePolicy.LocalOnly
    case other =>
      other
  }

  def download: F[Seq[((File, String), Either[ArtifactError, Unit])]] = {

    def res(url: String, keepHeaderChecksums: Boolean) = {
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
            def update = shouldDownload(file, url, checkRemote = true).flatMap {
              case true =>
                remoteKeepErrors(file, url, keepHeaderChecksums)
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
              case CachePolicy.LocalOnlyIfValid =>
                checkFileExists(file, url, log = false).flatMap { _ =>
                  shouldDownload(file, url, checkRemote = false).flatMap {
                    case true =>
                      EitherT[F, ArtifactError, Unit](S.point(Left(new ArtifactError.FileTooOldOrNotFound(file.toString))))
                    case false =>
                      EitherT(S.point[Either[ArtifactError, Unit]](Right(())))
                  }
                }
              case CachePolicy.UpdateChanging | CachePolicy.Update =>
                update
              case CachePolicy.FetchMissing =>
                checkFileExists(file, url).orElse(remoteKeepErrors(file, url, keepHeaderChecksums))
              case CachePolicy.ForceDownload =>
                remoteKeepErrors(file, url, keepHeaderChecksums)
            }
          }

        res.run.map((file, url) -> _)
    }

    val mainTask = res(artifact.url, keepHeaderChecksums = true)

    def checksumRes(c: String): Option[F[((File, String), Either[ArtifactError, Unit])]] =
      artifact.checksumUrls.get(c).map { url =>
        res(url, keepHeaderChecksums = false)
      }

    mainTask.flatMap { r =>
      val l0 = r match {
        case ((f, _), Right(())) =>
          val l = actualChecksums.map { c =>
            val candidate = FileCache.auxiliaryFile(f, c)
            S.delay(candidate.exists()).map {
              case false =>
                checksumRes(c).toSeq
              case true =>
                val url = artifact.checksumUrls.getOrElse(c, s"${artifact.url}.${c.toLowerCase(Locale.ROOT).filter(_ != '-')}")
                Seq(S.point[((File, String), Either[ArtifactError, Unit])](((candidate, url), Right(()))))
            }
          }
          S.gather(l).flatMap(l => S.gather(l.flatten))
        case _ =>
          val l = actualChecksums.flatMap(checksumRes)
          S.gather(l)
      }

      l0.map(r +: _)
    }
  }

}

object Downloader {

  private val checksumHeader = Seq("MD5", "SHA1", "SHA256")

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
                Left(new ArtifactError.NotFound(nfe.getMessage))
            }
          }

          val res = res0.getOrElse {
            Left(new ArtifactError.ConcurrentDownload(url))
          }

          Some(res)
        }
        catch {
          case _: javax.net.ssl.SSLException if retry >= 1 =>
            // TODO If Cache is made an (instantiated) class at some point, allow to log that exception.
            None
          case NonFatal(e) =>
            val ex = new ArtifactError.DownloadError(
              s"Caught $e${Option(e.getMessage).fold("")(" (" + _ + ")")} while downloading $url",
              Some(e)
            )
            if (java.lang.Boolean.getBoolean("coursier.cache.throw-exceptions"))
              throw ex
            Some(Left(ex))
        }

      resOpt match {
        case Some(res) => res
        case None =>
          helper(retry - 1)
      }
    }

    helper(sslRetry)
  }

  private def contentLength(
    url: String,
    authentication: Option[Authentication],
    followHttpToHttpsRedirections: Boolean,
    followHttpsToHttpRedirections: Boolean,
    credentials: Seq[DirectCredentials],
    sslSocketFactoryOpt: Option[SSLSocketFactory],
    hostnameVerifierOpt: Option[HostnameVerifier],
    logger: CacheLogger,
    maxRedirectionsOpt: Option[Int]
  ): Either[ArtifactError, Option[Long]] = {

    var conn: URLConnection = null

    try {
      conn = ConnectionBuilder(url)
        .withAuthentication(authentication)
        .withFollowHttpToHttpsRedirections(followHttpToHttpsRedirections)
        .withFollowHttpsToHttpRedirections(followHttpsToHttpRedirections)
        .withAutoCredentials(credentials)
        .withSslSocketFactoryOpt(sslSocketFactoryOpt)
        .withHostnameVerifierOpt(hostnameVerifierOpt)
        .withMethod("HEAD")
        .withMaxRedirectionsOpt(maxRedirectionsOpt)
        .connection()

      conn match {
        case c: HttpURLConnection =>
          logger.gettingLength(url)

          var success = false
          try {
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
          Left(new ArtifactError.DownloadError(s"Cannot do HEAD request with connection $other ($url)", None))
      }
    } finally {
      if (conn != null)
        CacheUrl.closeConn(conn)
    }
  }

}
