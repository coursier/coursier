package coursier.cache

import java.io.{Serializable => _, _}
import java.math.BigInteger
import java.net.{HttpURLConnection, URLConnection}
import java.nio.charset.StandardCharsets.UTF_8
import java.nio.file.{Files, StandardCopyOption}
import java.security.MessageDigest
import java.util.Locale
import java.util.concurrent.ExecutorService
import javax.net.ssl.{HostnameVerifier, SSLSocketFactory}

import coursier.cache.internal.FileUtil
import coursier.core.Authentication
import coursier.credentials.{Credentials, DirectCredentials, FileCredentials}
import coursier.paths.{CachePath, Util}
import coursier.util.{Artifact, EitherT, Sync, Task, WebPage}
import coursier.util.Monad.ops._
import dataclass.data

import scala.annotation.tailrec
import scala.concurrent.ExecutionContext
import scala.concurrent.duration.Duration
import scala.util.control.NonFatal

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
  sslRetry: Int = CacheDefaults.sslRetryCount,
  sslSocketFactoryOpt: Option[SSLSocketFactory] = None,
  hostnameVerifierOpt: Option[HostnameVerifier] = None,
  retry: Int = CacheDefaults.defaultRetryCount,
  bufferSize: Int = CacheDefaults.bufferSize
)(implicit
  sync: Sync[F]
) extends Cache[F] {

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

  import FileCache.{auxiliaryFile, checksumHeader, clearAuxiliaryFiles, readFullyTo, contentLength}

  override def loggerOpt: Some[CacheLogger] =
    Some(logger)

  private def download(
    artifact: Artifact,
    checksums: Set[String],
    cachePolicy: CachePolicy
  ): F[Seq[((File, String), Either[ArtifactError, Unit])]] = {

    // Reference file - if it exists, and we get not found errors on some URLs, we assume
    // we can keep track of these missing, and not try to get them again later.
    lazy val referenceFileOpt = artifact
      .extra
      .get("metadata")
      .map(a => localFile(a.url, a.authentication.map(_.user)))

    val cacheErrors = artifact.changing && artifact
      .extra
      .contains("cache-errors")

    def cacheErrors0: Boolean = cacheErrors || referenceFileOpt.exists(_.exists())

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
      EitherT(allCredentials.flatMap { allCredentials0 =>
        S.schedule(pool) {
          var conn: URLConnection = null

          try {
            conn = CacheUrl.urlConnection(
              url,
              artifact.authentication,
              followHttpToHttpsRedirections = followHttpToHttpsRedirections,
              followHttpsToHttpRedirections = followHttpsToHttpRedirections,
              credentials = allCredentials0,
              sslSocketFactoryOpt,
              hostnameVerifierOpt,
              method = "HEAD",
              maxRedirectionsOpt = maxRedirections
            )

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
    def doTouchCheckFile(file: File, url: String, updateLinks: Boolean): Unit = {
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
        val linkFile = auxiliaryFile(file, "links")

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

    def shouldDownload(file: File, url: String, checkRemote: Boolean): EitherT[F, ArtifactError, Boolean] = {
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

    def remote(
      file: File,
      url: String,
      keepHeaderChecksums: Boolean
    ): EitherT[F, ArtifactError, Unit] =
      EitherT(allCredentials.flatMap { allCredentials0 =>
        S.schedule(pool) {

          val tmp = CachePath.temporaryFile(file)

          var lenOpt = Option.empty[Option[Long]]

          def doDownload(): Either[ArtifactError, Unit] =
            FileCache.downloading(url, file, sslRetry) {

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
                val (conn0, partialDownload) = CacheUrl.urlConnectionMaybePartial(
                  url,
                  authenticationOpt,
                  alreadyDownloaded,
                  followHttpToHttpsRedirections,
                  followHttpsToHttpRedirections,
                  allCredentials0
                    .filter(_.matchHost), // just in case
                  sslSocketFactoryOpt,
                  hostnameVerifierOpt,
                  "GET",
                  maxRedirectionsOpt = maxRedirections
                )
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
                          checksumHeader.flatMap { c =>
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
                      try readFullyTo(in, out, logger, url, if (partialDownload) alreadyDownloaded else 0L, bufferSize)
                      finally out.close()
                    } finally in.close()

                  clearAuxiliaryFiles(file)

                  for ((key, data) <- auxiliaryData) {
                    val dest = auxiliaryFile(file, key)
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
                  contentLength(
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
                  contentLength(
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

    def errFile(file: File) = new File(file.getParentFile, "." + file.getName + ".error")

    def remoteKeepErrors(file: File, url: String, keepHeaderChecksums: Boolean): EitherT[F, ArtifactError, Unit] = {

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

    def checkFileExists(file: File, url: String,
                        log: Boolean = true): EitherT[F, ArtifactError, Unit] =
      EitherT {
        S.schedule(pool) {
          if (file.exists()) {
            logger.foundLocally(url)
            Right(())
          } else
            Left(new ArtifactError.NotFound(file.toString))
        }
      }

    val cachePolicy0 = cachePolicy match {
      case CachePolicy.UpdateChanging if !artifact.changing =>
        CachePolicy.FetchMissing
      case CachePolicy.LocalUpdateChanging | CachePolicy.LocalOnlyIfValid if !artifact.changing =>
        CachePolicy.LocalOnly
      case other =>
        other
    }

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
          val l = checksums
            .toSeq
            .map { c =>
              val candidate = auxiliaryFile(f, c)
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
          val l = checksums
            .toSeq
            .flatMap { c =>
              checksumRes(c)
            }
          S.gather(l)
      }

      l0.map(r +: _)
    }
  }

  def validateChecksum(
    artifact: Artifact,
    sumType: String
  ): EitherT[F, ArtifactError, Unit] = {

    val localFile0 = localFile(artifact.url, artifact.authentication.map(_.user))

    val headerSumFile = Seq(auxiliaryFile(localFile0, sumType))
    val downloadedSumFile = artifact.checksumUrls.get(sumType).map(sumUrl => localFile(sumUrl, artifact.authentication.map(_.user)))

    EitherT {
      S.schedule(pool) {
        (headerSumFile ++ downloadedSumFile.toSeq).find(_.exists()) match {
          case Some(sumFile) =>

            val sumOpt = CacheChecksum.parseRawChecksum(Files.readAllBytes(sumFile.toPath))

            sumOpt match {
              case None =>
                Left(new ArtifactError.ChecksumFormatError(sumType, sumFile.getPath))

              case Some(sum) =>
                val md = MessageDigest.getInstance(sumType)

                var is: FileInputStream = null
                try {
                  is = new FileInputStream(localFile0)
                  FileUtil.withContent(is, new FileUtil.UpdateDigest(md))
                } finally is.close()

                val digest = md.digest()
                val calculatedSum = new BigInteger(1, digest)

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
            Left(new ArtifactError.ChecksumNotFound(sumType, localFile0.getPath)): Either[ArtifactError, Unit]
        }
      }
    }
  }

  private val checksums0 = if (checksums.isEmpty) Seq(None) else checksums

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
      } else
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
  ): EitherT[F, ArtifactError, File] = {

    EitherT {
      download(
        artifact,
        checksums = checksums0.collect { case Some(c) => c }.toSet,
        cachePolicy = policy
      ).map { results =>
        val resultsMap = results
          .map {
            case ((_, u), b) => u -> b
          }
          .toMap

        val checksumResults = checksums0.map {
          case None => None
          case Some(c) =>
            val url = artifact.checksumUrls.getOrElse(c, s"${artifact.url}.${c.toLowerCase(Locale.ROOT).filter(_ != '-')}")
            Some((c, url, resultsMap.get(url)))
        }
        val checksum = checksumResults.collectFirst {
          case None => None
          case Some((c, _, Some(Right(())))) =>
            Some(c)
        }
        def checksumErrors: Seq[(String, String)] = checksumResults.collect {
          case Some((c, url, None)) =>
            // shouldn't happen, the download method must have returned results for this…
            c -> s"$url not downloaded"
          case Some((c, _, Some(Left(e)))) =>
            c -> e.describe
        }

        val ((f, _), res) = results.head
        res.flatMap { _ =>
          checksum match {
            case None =>
              // FIXME All the checksums should be in the error, possibly with their URLs
              //       from artifact0.checksumUrls
              Left(new ArtifactError.ChecksumErrors(checksumErrors))
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
        val badFile = localFile(artifact.url, artifact.authentication.map(_.user))
        val badChecksumFile = new File(err.sumFile)
        val foundBadFileInCache = {
          val location0 = location.getCanonicalPath.stripSuffix("/") + "/"
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
      case err =>
        EitherT(S.point(Left(err)))
    }
  }

  def file(artifact: Artifact): EitherT[F, ArtifactError, File] =
    file(artifact, retry)

  def file(artifact: Artifact, retry: Int): EitherT[F, ArtifactError, File] =
    cachePolicies.tail.map(filePerPolicy(artifact, _, retry))
      .foldLeft(filePerPolicy(artifact, cachePolicies.head, retry))(_ orElse _)

  private def fetchPerPolicy(artifact: Artifact, policy: CachePolicy): EitherT[F, String, String] = {

    val (artifact0, links) =
      if (artifact.url.endsWith("/.links")) (artifact.withUrl(artifact.url.stripSuffix(".links")), true)
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
            } else
              new String(Files.readAllBytes(f.toPath), UTF_8)
          Right(content)
        }
        catch {
          case NonFatal(e) =>
            Left(s"Could not read (file:${f.getCanonicalPath}): ${e.getMessage}")
        }

      val res = if (f.exists()) {
        if (f.isDirectory) {
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
  }

  def fetch: Cache.Fetch[F] =
    a =>
      cachePolicies.tail.foldLeft(fetchPerPolicy(a, cachePolicies.head))(_ orElse fetchPerPolicy(a, _))

  override def fetchs: Seq[Cache.Fetch[F]] =
    cachePolicies.map { p =>
      (a: Artifact) =>
        fetchPerPolicy(a, p)
    }

  lazy val ec = ExecutionContext.fromExecutorService(pool)

}

object FileCache {

  private[coursier] def localFile0(url: String, cache: File, user: Option[String], localArtifactsShouldBeCached: Boolean): File =
    CachePath.localFile(url, cache, user.orNull, localArtifactsShouldBeCached)

  private def auxiliaryFilePrefix(file: File): String =
    s".${file.getName}__"

  private def clearAuxiliaryFiles(file: File): Unit = {
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
      conn = CacheUrl.urlConnection(
        url,
        authentication,
        followHttpToHttpsRedirections = followHttpToHttpsRedirections,
        followHttpsToHttpRedirections = followHttpsToHttpRedirections,
        credentials = credentials,
        sslSocketFactoryOpt = sslSocketFactoryOpt,
        hostnameVerifierOpt = hostnameVerifierOpt,
        method = "HEAD",
        maxRedirectionsOpt = maxRedirectionsOpt
      )

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


  def apply[F[_]]()(implicit S: Sync[F] = Task.sync): FileCache[F] =
    FileCache(CacheDefaults.location)(S)


  private val checksumHeader = Seq("MD5", "SHA1", "SHA256")

}
