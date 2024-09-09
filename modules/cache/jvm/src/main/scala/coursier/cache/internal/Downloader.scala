package coursier.cache.internal

import java.io.{Serializable => _, _}
import java.net.{HttpURLConnection, URLConnection, MalformedURLException}
import java.nio.charset.StandardCharsets.UTF_8
import java.nio.file.{Files, StandardCopyOption}
import java.time.Clock
import java.util.Locale
import java.util.concurrent.ExecutorService
import java.util.zip.GZIPInputStream
import javax.net.ssl.{HostnameVerifier, SSLSocketFactory}

import coursier.cache._
import coursier.core.Authentication
import coursier.credentials.DirectCredentials
import coursier.paths.{CachePath, Util}
import coursier.util.{Artifact, EitherT, Sync, Task, WebPage}
import coursier.util.Monad.ops._
import dataclass._

import scala.annotation.tailrec
import scala.concurrent.duration.Duration
import scala.util.control.NonFatal

// format: off
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
  bufferSize: Int = CacheDefaults.bufferSize,
  @since("2.0.16")
    classLoaders: Seq[ClassLoader] = Nil,
  @since("2.1.0-RC3")
    clock: Clock = Clock.systemDefaultZone()
)(implicit
  S: Sync[F]
) {
  // format: on

  private def blockingIO[T](f: => T): F[T] =
    S.schedule(pool)(f)
  private def blockingIOE[T](f: => Either[ArtifactError, T]): EitherT[F, ArtifactError, T] =
    EitherT(S.schedule(pool)(f))

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

  private object Blocking {

    def fileLastModified(file: File): Either[ArtifactError, Option[Long]] =
      Right(Some(file.lastModified()).filter(_ > 0L))

    def urlLastModified(
      url: String,
      currentLastModifiedOpt: Option[Long], // for the logger
      logger: CacheLogger,
      allCredentials0: Seq[DirectCredentials]
    ): Either[ArtifactError, Option[Long]] = {
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
          .withClassLoaders(classLoaders)
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
            }
            finally if (!success)
                logger.checkingUpdatesResult(url, currentLastModifiedOpt, None)

          case other =>
            Left(
              new ArtifactError.DownloadError(
                s"Cannot do HEAD request with connection $other ($url)",
                None
              )
            )
        }
      }
      catch {
        case NonFatal(e) =>
          val ex = new ArtifactError.DownloadError(
            s"Caught $e${Option(e.getMessage).fold("")(" (" + _ + ")")} while getting last modified time of $url",
            Some(e)
          )
          if (Downloader.throwExceptions)
            throw ex
          Left(ex)
      }
      finally if (conn != null)
          CacheUrl.closeConn(conn)
    }

    def lastCheck(file: File): Option[Long] =
      for {
        f <- Some(ttlFile(file))
        if f.exists()
        ts = f.lastModified()
        if ts > 0L
      } yield ts

    def doTouchCheckFile(file: File, url: String, updateLinks: Boolean): Unit = {
      val ts = clock.millis()
      val f  = ttlFile(file)
      if (!f.exists()) {
        val fos = new FileOutputStream(f)
        fos.write(Array.empty[Byte])
        fos.close()
      }
      f.setLastModified(ts)

      if (updateLinks && file.getName == ".directory") {
        val linkFile = FileCache.auxiliaryFile(file, "links")

        val succeeded =
          try {
            val content =
              WebPage.listElements(url, new String(Files.readAllBytes(file.toPath), UTF_8))
                .mkString("\n")

            var fos: FileOutputStream = null
            try {
              fos = new FileOutputStream(linkFile)
              fos.write(content.getBytes(UTF_8))
            }
            finally if (fos != null)
                fos.close()
            true
          }
          catch {
            case NonFatal(_) =>
              false
          }

        if (!succeeded)
          Files.deleteIfExists(linkFile.toPath)
      }
    }

    def doDownload(
      file: File,
      url: String,
      keepHeaderChecksums: Boolean,
      allCredentials0: Seq[DirectCredentials],
      tmp: File
    ): Either[ArtifactError, Unit] = {

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
          .withClassLoaders(classLoaders)
          .connectionMaybePartial()
        conn = conn0

        val respCodeOpt = CacheUrl.responseCode(conn)

        if (respCodeOpt.contains(404))
          Left(new ArtifactError.NotFound(url, permanent = Some(true)))
        else if (respCodeOpt.contains(403))
          Left(new ArtifactError.Forbidden(url))
        else if (respCodeOpt.contains(401))
          Left(new ArtifactError.Unauthorized(url, realm = CacheUrl.realm(conn)))
        else {
          for (len0 <- Option(conn.getContentLengthLong) if len0 >= 0L) {
            val len = len0 + (if (partialDownload) alreadyDownloaded else 0L)
            logger.downloadLength(url, len, alreadyDownloaded, watching = false)
          }

          val auxiliaryData: Map[String, Array[Byte]] =
            conn match {
              case conn0: HttpURLConnection if keepHeaderChecksums =>
                def entries = for {
                  c   <- Downloader.checksumHeader.iterator
                  str <- Option(conn0.getHeaderField(s"X-Checksum-$c")).iterator
                } yield (c, str.getBytes(UTF_8))
                entries.toMap
              case _ => Map()
            }

          val lastModifiedOpt = Option(conn.getLastModified).filter(_ > 0L)

          val in = {
            val baseStream =
              if (conn.getContentEncoding == "gzip") new GZIPInputStream(conn.getInputStream)
              else conn.getInputStream
            new BufferedInputStream(baseStream, bufferSize)
          }

          val result =
            try {
              val out = CacheLocks.withStructureLock(location) {
                Util.createDirectories(tmp.toPath.getParent);
                new FileOutputStream(tmp, partialDownload)
              }
              try Downloader.readFullyTo(
                  in,
                  out,
                  logger,
                  url,
                  if (partialDownload) alreadyDownloaded else 0L,
                  bufferSize
                )
              finally out.close()
            }
            finally in.close()

          FileCache.clearAuxiliaryFiles(file)

          for ((key, data) <- auxiliaryData) {
            val dest    = FileCache.auxiliaryFile(file, key)
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

          Blocking.doTouchCheckFile(file, url, updateLinks = true)

          Right(result)
        }
      }
      finally if (conn != null)
          CacheUrl.closeConn(conn)
    }

    def checkDownload(
      file: File,
      url: String,
      allCredentials0: Seq[DirectCredentials],
      tmp: File
    ): Option[Either[ArtifactError, Unit]] = {

      var lenOpt = Option.empty[Option[Long]]

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
        }
        else
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
        }
        else
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
      }
      else {
        // yes, Thread.sleep. 'tis our thread pool anyway.
        // (And the various resources make it not straightforward to switch to a more Task-based internal API here.)
        Thread.sleep(20L)

        val currentLen = tmp.length()

        if (currentLen == 0L && file.exists()) { // check again if file exists in case it was created in the mean time
          done()
          Some(Right(()))
        }
        else {
          progress(currentLen)
          None
        }
      }
    }

    def remote(
      file: File,
      url: String,
      keepHeaderChecksums: Boolean,
      allCredentials0: Seq[DirectCredentials],
      proceed: () => Boolean
    ): Either[ArtifactError, Unit] = {

      val tmp = CachePath.temporaryFile(file)

      logger.downloadingArtifact(url, artifact)

      var success = false

      try {
        val res = Downloader.downloading(url, file, sslRetry)(
          CacheLocks.withLockOr(location, file)(
            if (proceed())
              doDownload(file, url, keepHeaderChecksums, allCredentials0, tmp)
            else
              Right(()),
            checkDownload(file, url, allCredentials0, tmp)
          ),
          checkDownload(file, url, allCredentials0, tmp)
        )
        success = res.isRight
        res
      }
      finally logger.downloadedArtifact(url, success = success)
    }

  }

  private def urlLastModified(
    url: String,
    currentLastModifiedOpt: Option[Long],
    logger: CacheLogger
  ): F[Either[ArtifactError, Option[Long]]] =
    allCredentials.flatMap { allCredentials0 =>
      blockingIO {
        Blocking.urlLastModified(url, currentLastModifiedOpt, logger, allCredentials0)
      }
    }

  private def ttlFile(file: File): File =
    new File(file.getParent, s".${file.getName}.checked")

  private def checkNeeded(file: File) = ttl match {
    case None                       => S.point(true)
    case Some(ttl) if !ttl.isFinite => S.point(false)
    case Some(ttl) =>
      blockingIO {
        Blocking.lastCheck(file).fold(true) { ts =>
          val now = clock.millis()
          now > ts + ttl.toMillis
        }
      }
  }

  private def checkNeededBlocking(file: File) = ttl match {
    case None                       => true
    case Some(ttl) if !ttl.isFinite => false
    case Some(ttl) =>
      Blocking.lastCheck(file).fold(true) { ts =>
        val now = clock.millis()
        now > ts + ttl.toMillis
      }
  }

  private def shouldDownload(
    file: File,
    url: String,
    checkRemote: Boolean
  ): EitherT[F, ArtifactError, Boolean] = {

    def checkErrFile: Either[ArtifactError, Unit] = {
      val errFile0 = errFile(file)

      if (referenceFileOpt.exists(_.exists()) && errFile0.exists())
        Left(new ArtifactError.NotFound(url, Some(true)))
      else if (cacheErrors && errFile0.exists()) {
        val ts  = errFile0.lastModified()
        val now = clock.millis()
        if (ts > 0L && (ttl.exists(!_.isFinite) || now < ts + ttl.fold(0L)(_.toMillis)))
          Left(new ArtifactError.NotFound(url))
        else
          Right(())
      }
      else
        Right(())
    }

    def doCheckRemote = for {
      fileLastModOpt <- blockingIOE(Blocking.fileLastModified(file))
      urlLastModOpt  <- EitherT(urlLastModified(url, fileLastModOpt, logger))
    } yield {
      val fromDatesOpt = for {
        fileLastMod <- fileLastModOpt
        urlLastMod  <- urlLastModOpt
      } yield fileLastMod < urlLastMod

      fromDatesOpt.getOrElse(true)
    }

    def checkShouldDownload: F[Either[ArtifactError, Boolean]] =
      blockingIO(file.exists()).flatMap {
        case false => S.point(Right(true))
        case true =>
          checkNeeded(file).flatMap {
            case false => S.point(Right(false))
            case true =>
              if (checkRemote)
                doCheckRemote.run.flatMap {
                  case Right(false) =>
                    blockingIO {
                      Blocking.doTouchCheckFile(file, url, updateLinks = false)
                      Right(false)
                    }
                  case other => S.point(other)
                }
              else
                S.point(Right(true))
          }
      }

    for {
      _   <- blockingIOE(checkErrFile)
      res <- EitherT(checkShouldDownload)
    } yield res
  }

  private def shouldDownloadSecondCheckBlocking(file: File): Boolean =
    !file.exists() || checkNeededBlocking(file)

  private def remote(
    file: File,
    url: String,
    keepHeaderChecksums: Boolean,
    proceed: () => Boolean
  ): F[Either[ArtifactError, Unit]] =
    allCredentials.flatMap { allCredentials0 =>
      blockingIO(Blocking.remote(file, url, keepHeaderChecksums, allCredentials0, proceed))
    }

  private def errFile(file: File) = new File(file.getParentFile, "." + file.getName + ".error")

  private def remoteKeepErrors(
    file: File,
    url: String,
    keepHeaderChecksums: Boolean,
    proceed: () => Boolean
  ): F[Either[ArtifactError, Unit]] = {

    val errFile0 = errFile(file)

    def createErrFileBlocking() =
      if (cacheErrors0) {
        val p = errFile0.toPath
        Util.createDirectories(p.getParent)
        Files.write(p, Array.emptyByteArray)
      }

    def deleteErrFileBlocking() =
      if (errFile0.exists())
        errFile0.delete()

    remote(file, url, keepHeaderChecksums, proceed).flatMap { e =>
      blockingIO {
        e match {
          case err @ Left(nf: ArtifactError.NotFound) if nf.permanent.contains(true) =>
            createErrFileBlocking()
            err: Either[ArtifactError, Unit]
          case other =>
            deleteErrFileBlocking()
            other
        }
      }
    }
  }

  private def checkFileExistsBlocking(
    file: File,
    url: String
  ): Boolean =
    file.exists() && {
      logger.foundLocally(url)
      true
    }

  private def checkFileExists(
    file: File,
    url: String,
    log: Boolean = true // ignored???
  ): F[Either[ArtifactError, Unit]] =
    blockingIO {
      if (checkFileExistsBlocking(file, url))
        Right(())
      else
        Left(new ArtifactError.NotFound(file.toString))
    }

  private val actualCachePolicy: CachePolicy.Mixed = cachePolicy.acceptChanging match {
    case CachePolicy.UpdateChanging if !artifact.changing =>
      CachePolicy.FetchMissing
    case CachePolicy.LocalUpdateChanging | CachePolicy.LocalOnlyIfValid if !artifact.changing =>
      CachePolicy.LocalOnly
    case other =>
      other
  }

  private def downloadUrl(url: String, keepHeaderChecksums: Boolean): F[DownloadResult] = {

    logger.checkingArtifact(url, artifact)

    val file = localFile(url, artifact.authentication.map(_.user))

    def run =
      if (url.startsWith("file:/") && !localArtifactsShouldBeCached)
        // for debug purposes, flaky with URL-encoded chars anyway
        // def filtered(s: String) =
        //   s.stripPrefix("file:/").stripPrefix("//").stripSuffix("/")
        // assert(
        //   filtered(url) == filtered(file.toURI.toString),
        //   s"URL: ${filtered(url)}, file: ${filtered(file.toURI.toString)}"
        // )
        checkFileExists(file, url)
      else {
        def maybeUpdate = for {
          needsUpdate <- shouldDownload(file, url, checkRemote = true)
          _ <- {
            val f: F[Either[ArtifactError, Unit]] =
              if (needsUpdate)
                remoteKeepErrors(
                  file,
                  url,
                  keepHeaderChecksums,
                  () => shouldDownloadSecondCheckBlocking(file)
                )
              else
                S.point(Right(()))
            EitherT(f)
          }
        } yield ()

        actualCachePolicy match {
          case CachePolicy.LocalOnly =>
            checkFileExists(file, url)
          case CachePolicy.LocalUpdateChanging | CachePolicy.LocalUpdate =>
            val e = for {
              _ <- EitherT(checkFileExists(file, url, log = false))
              _ <- maybeUpdate
            } yield ()
            e.run
          case CachePolicy.LocalOnlyIfValid =>
            val e = for {
              _           <- EitherT(checkFileExists(file, url, log = false))
              needsUpdate <- shouldDownload(file, url, checkRemote = false)
              _ <- {
                val e: Either[ArtifactError, Unit] =
                  if (needsUpdate) Left(new ArtifactError.FileTooOldOrNotFound(file.toString))
                  else Right(())
                EitherT(S.point(e))
              }
            } yield ()
            e.run
          case CachePolicy.UpdateChanging | CachePolicy.Update =>
            maybeUpdate.run
          case CachePolicy.FetchMissing =>
            EitherT(checkFileExists(file, url))
              .orElse {
                EitherT(
                  remoteKeepErrors(
                    file,
                    url,
                    keepHeaderChecksums,
                    () => !checkFileExistsBlocking(file, url)
                  )
                )
              }
              .run
          case CachePolicy.ForceDownload =>
            remoteKeepErrors(file, url, keepHeaderChecksums, () => true)
        }
      }

    val run0 =
      if (artifact.changing && !cachePolicy.acceptsChangingArtifacts)
        S.point(Left(new ArtifactError.ForbiddenChangingArtifact(url)))
      else
        run

    run0.map(e => DownloadResult(url, file, e.left.toOption))
  }

  def download: F[Seq[DownloadResult]] = {

    val mainTask = downloadUrl(artifact.url, keepHeaderChecksums = true)

    def checksumRes(c: String): Seq[F[DownloadResult]] =
      artifact.checksumUrls.get(c).toSeq.map { url =>
        downloadUrl(url, keepHeaderChecksums = false)
      }

    mainTask.flatMap { r =>
      val l0 =
        if (r.errorOpt.isEmpty) {
          val l = actualChecksums.map { c =>
            val candidate = FileCache.auxiliaryFile(r.file, c)
            blockingIO(candidate.exists()).map {
              case false => checksumRes(c)
              case true =>
                def fallbackUrl = s"${artifact.url}.${c.toLowerCase(Locale.ROOT).filter(_ != '-')}"
                val url         = artifact.checksumUrls.getOrElse(c, fallbackUrl)
                Seq(S.point(DownloadResult(url, candidate)))
            }
          }
          S.gather(l).flatMap(l => S.gather(l.flatten))
        }
        else {
          val l = actualChecksums.flatMap(checksumRes)
          S.gather(l)
        }
      l0.map(r +: _)
    }
  }

}

object Downloader {

  private lazy val throwExceptions = java.lang.Boolean.getBoolean("coursier.cache.throw-exceptions")

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
    f: => Either[ArtifactError, T],
    ifLocked: => Option[Either[ArtifactError, T]]
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

          res0.orElse(ifLocked)
        }
        catch {
          case NonFatal(e) if throwExceptions =>
            val ex = new ArtifactError.DownloadError(
              s"Caught ${e.getClass().getName()}${Option(e.getMessage).fold("")(" (" + _ + ")")} while downloading $url",
              Some(e)
            )
            throw ex

          case UnknownProtocol(e, msg0) =>
            val docUrl = "https://get-coursier.io/docs/extra.html#extra-protocols"

            val msg = List(
              s"Caught ${e.getClass.getName} ($msg0) while downloading $url.",
              s"Visit $docUrl to learn how to handle custom protocols."
            ).mkString(" ")

            val ex = new ArtifactError.DownloadError(msg, Some(e))

            Some(Left(ex))

          case _: javax.net.ssl.SSLException if retry >= 1 =>
            // TODO If Cache is made an (instantiated) class at some point, allow to log that exception.
            None

          case NonFatal(e) =>
            val ex = new ArtifactError.DownloadError(
              s"Caught ${e.getClass().getName()}${Option(e.getMessage).fold("")(" (" + _ + ")")} while downloading $url",
              Some(e)
            )
            Some(Left(ex))
        }

      resOpt match {
        case Some(res) => res
        case None      => helper(retry - 1)
      }
    }

    helper(sslRetry)
  }

  private object UnknownProtocol {
    def unapply(t: Throwable): Option[(MalformedURLException, String)] = t match {
      case ex: MalformedURLException if ex.getMessage.startsWith("unknown protocol: ") =>
        Some((ex, ex.getMessage))
      case _ => None
    }
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
          }
          finally if (!success)
              logger.gettingLengthResult(url, None)

        case other =>
          Left(
            new ArtifactError.DownloadError(
              s"Cannot do HEAD request with connection $other ($url)",
              None
            )
          )
      }
    }
    finally if (conn != null)
        CacheUrl.closeConn(conn)
  }

}
