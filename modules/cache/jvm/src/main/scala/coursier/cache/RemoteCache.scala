package coursier.cache

import com.github.plokhotnyuk.jsoniter_scala.core._
import coursier.cache.internal.RemoteCacheHelpers
import coursier.cache.server.Model
import coursier.cache.server.Model.{Artifact => ModelArtifact, _}
import coursier.paths.CachePath
import coursier.util.{Artifact, EitherT, Sync, Task, WebPage}
import dataclass.{data, since => unroll}

import java.io.{ByteArrayOutputStream, File}
import java.net.{HttpURLConnection, URI, URL}
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.util.Base64
import java.util.concurrent.{ConcurrentHashMap, ExecutorService}

import scala.cli.config.Secret
import scala.concurrent.duration.Duration
import scala.concurrent.{ExecutionContext, ExecutionContextExecutorService}
import scala.util.Try

@data(
  deprecatedSetters = true,
  deprecatedSettersMessage = "Use copy instead",
  deprecatedSettersSince = "2.1.25"
) case class RemoteCache[F[_]](
  serverUrl: String,
  location: File,
  basicAuth: Option[Secret[String]] = None, // user:password
  pool: ExecutorService = CacheDefaults.pool,
  logger: CacheLogger = CacheLogger.nop,
  cachePolicies: Seq[CachePolicy] = CacheDefaults.cachePolicies,
  watchLenPool: ExecutorService = CacheDefaults.watchLenPool,
  fileFallback: Option[FileCache[F]] = None,
  /** TTL for changing artifacts, sent to the server along with each request
    *
    * Same meaning as `FileCache.ttl`. Servers predating this field ignore it, and use their own
    * TTL.
    */
  @unroll
  ttl: Option[Duration] = CacheDefaults.ttl
)(implicit
  val sync: Sync[F]
) extends Cache[F] with Cache.HasLocation with Cache.HasExecutionContext
    with Cache.WithLogger[F, RemoteCache[F]] with Cache.Default[F] with RemoteCacheHelpers[F] {

  /** Binary compatibility stub, not meant to be called from source
    *
    * The default of `copy`'s implicit parameter list, as coursier 2.1.25 compiled it: code built
    * against that version calls it, then the `copy` overload with the same fields, when it calls
    * `copy` without an implicit `Sync` in scope. Its index moves with every field added, and the
    * compiler only generates the current one, so the former ones have to be kept by hand, with the
    * fields of their time.
    */
  private[cache] def copy$default$9(
    serverUrl: String,
    location: File,
    basicAuth: Option[Secret[String]],
    pool: ExecutorService,
    logger: CacheLogger,
    cachePolicies: Seq[CachePolicy],
    watchLenPool: ExecutorService,
    fileFallback: Option[FileCache[F]]
  ): Sync[F] = sync

  lazy val ec: ExecutionContextExecutorService =
    ExecutionContext.fromExecutorService(pool)

  private val onGoing = new ConcurrentHashMap[String, RemoteCache.OnGoingDownload]

  // The fallback handles file: URLs locally, it mustn't defer to a cache server itself
  private lazy val fileFallback0 = fileFallback.map(_.copy(allowCacheSubstitution = false))

  private lazy val (getUrl, pathUrl, actualBasicAuthOpt) = {
    val rawGetUri  = new URI(s"$serverUrl/get")
    val rawPathUri = new URI(s"$serverUrl/path")
    Option(rawGetUri.getRawUserInfo) match {
      case Some(userInfo) =>
        def strip(uri: URI): URL = new URI(
          uri.getScheme,
          null, // userInfo
          uri.getHost,
          uri.getPort,
          uri.getPath,
          uri.getQuery,
          uri.getFragment
        ).toURL
        (strip(rawGetUri), strip(rawPathUri), Some(Secret(userInfo)))
      case None =>
        (rawGetUri.toURL, rawPathUri.toURL, basicAuth)
    }
  }

  private def postToUrl[R: JsonValueCodec](
    url: URL,
    body: Array[Byte]
  ): Either[ArtifactError, R] = {
    val conn = url.openConnection()
      .asInstanceOf[HttpURLConnection]
    try {
      conn.setRequestMethod("POST")
      conn.setDoOutput(true)
      conn.setRequestProperty("Content-Type", "application/json")
      for (secret <- actualBasicAuthOpt)
        conn.setRequestProperty(
          "Authorization",
          "Basic " + Base64.getEncoder.encodeToString(
            secret.value.getBytes(StandardCharsets.UTF_8)
          )
        )
      conn.getOutputStream.write(body)
      conn.getOutputStream.close()

      val code   = conn.getResponseCode
      val stream = if (code >= 400) conn.getErrorStream else conn.getInputStream
      val baos   = new ByteArrayOutputStream
      if (stream != null) {
        val buf = new Array[Byte](8192)
        var n   = 0
        while ({ n = stream.read(buf); n != -1 })
          baos.write(buf, 0, n)
        stream.close()
      }

      if (code == 200)
        Right(readFromArray[R](baos.toByteArray))
      else
        Left(
          new ArtifactError.DownloadError(
            s"Server returned HTTP $code: ${new String(baos.toByteArray, StandardCharsets.UTF_8)}",
            None
          )
        )
    }
    finally
      conn.disconnect()
  }

  private def watcher(url: String, entry: RemoteCache.OnGoingDownload): Runnable =
    () => {
      var lenOpt     = Option.empty[Long]
      var currentLen = 0L

      try
        while (!entry.done && !entry.errored) {
          Thread.sleep(20L)

          val newLen = entry.tmp.length()

          if (newLen > 0) {
            if (!entry.started) {
              entry.started = true
              logger.downloadingArtifact(url, entry.artifact)
            }

            if (lenOpt.isEmpty) {
              if (entry.lenFile.exists() && entry.lenFile.length() == 8L) {
                val bytes = Files.readAllBytes(entry.lenFile.toPath)
                if (bytes.length == 8) {
                  val len = ByteBuffer.wrap(bytes).getLong
                  lenOpt = Some(len)
                  logger.downloadLength(url, len, currentLen, watching = true)
                }
              }
            }
            else if (newLen != currentLen) {
              currentLen = newLen
              logger.downloadProgress(url, currentLen)
            }
          }
        }
      finally {
        onGoing.remove(url)

        if (entry.started) {
          if (entry.done && entry.file.exists()) {
            val len = lenOpt.getOrElse(entry.file.length())
            if (lenOpt.isEmpty)
              logger.downloadLength(url, len, len, watching = true)
            logger.downloadProgress(url, len)
          }

          if (entry.done || entry.errored)
            logger.downloadedArtifact(url, success = !entry.errored)
        }
      }
    }

  private def fileWithPolicy(
    artifact: Artifact,
    cachePolicy: Option[String]
  ): EitherT[F, ArtifactError, File] =
    EitherT {
      Sync[F].schedule(pool) {
        val url = artifact.url

        val pathRequest = PathRequest(ModelArtifact.fromArtifact(artifact))
        val pathBody    = writeToArray(pathRequest)

        val pathInfoEither: Either[ArtifactError, String] =
          postToUrl[PathResponse](pathUrl, pathBody).flatMap { pathResponse =>
            pathResponse.error match {
              case Some(err) =>
                Left(new ArtifactError.DownloadError(s"${err.`type`}: ${err.message}", None))
              case None =>
                pathResponse.path match {
                  case Some(relativePath) =>
                    val elems = relativePath.split("/")
                    if (elems.contains(".") || elems.contains(".."))
                      Left(new ArtifactError.DownloadError("Server returned an invalid path", None))
                    else
                      Right(relativePath)
                  case None =>
                    Left(new ArtifactError.DownloadError(
                      "Server returned no path and no error",
                      None
                    ))
                }
            }
          }

        pathInfoEither.flatMap { relativePath =>
          val request = GetRequest(
            ModelArtifact.fromArtifact(artifact),
            cachePolicy = cachePolicy,
            // without a specific policy, the server tries ours in order, like FileCache.file does
            cachePolicies =
              if (cachePolicy.isEmpty) cachePolicies.map(Model.cachePolicyName) else Nil,
            ttl = Some(Model.serializeTtl(ttl))
          )
          val body = writeToArray(request)

          val entry = {
            val file    = new File(location, relativePath)
            val tmp     = CachePath.temporaryFile(file)
            val lenFile = new File(tmp.getPath + ".length")
            new RemoteCache.OnGoingDownload(file, tmp, lenFile, artifact)
          }
          val existing = onGoing.putIfAbsent(url, entry)
          if (existing == null) {
            if (!entry.file.exists()) {
              entry.started = true
              logger.downloadingArtifact(url, artifact)
            }
            watchLenPool.submit(watcher(url, entry))
          }

          var success = false
          try {
            val result = postToUrl[GetResponse](getUrl, body).flatMap { response =>
              response.error match {
                case Some(err) =>
                  Left(new ArtifactError.DownloadError(s"${err.`type`}: ${err.message}", None))
                case None =>
                  response.path match {
                    case Some(relativePath) =>
                      val elems = relativePath.split("/")
                      if (elems.contains(".") || elems.contains(".."))
                        Left(new ArtifactError.DownloadError(
                          "Server returned an invalid path",
                          None
                        ))
                      else
                        Right(new File(location, relativePath))
                    case None =>
                      Left(new ArtifactError.DownloadError(
                        "Server returned no path and no error",
                        None
                      ))
                  }
              }
            }
            success = result.isRight
            result
          }
          finally
            if (success) entry.done = true
            else entry.errored = true
        }
      }
    }

  def file(artifact: Artifact): EitherT[F, ArtifactError, File] = {
    val artifact0 =
      if (artifact.url.endsWith("/.links")) artifact.copy(url = artifact.url.stripSuffix(".links"))
      else artifact
    fileFallback0.filter(_ => artifact0.url.startsWith("file:/")) match {
      case Some(fallback) =>
        fallback.file(artifact0)
      case None =>
        fileWithPolicy(artifact0, None)
    }
  }

  private def fetchWithPolicy(cachePolicy: Option[String]): Cache.Fetch[F] =
    artifact => {
      val (artifact0, links) =
        if (artifact.url.endsWith("/.links"))
          (artifact.copy(url = artifact.url.stripSuffix(".links")), true)
        else (artifact, false)
      fileWithPolicy(artifact0, cachePolicy).leftMap(_.describe).flatMap { f =>
        EitherT {
          Sync[F].schedule(pool) {
            if (!f.exists())
              Left(s"File not found: $f")
            else if (links) {
              val linkFile = FileCache.auxiliaryFile(f, "links")
              if (f.getName == ".directory" && linkFile.isFile)
                Right(new String(Files.readAllBytes(linkFile.toPath), StandardCharsets.UTF_8))
              else
                Right(
                  WebPage.listElements(
                    artifact0.url,
                    new String(Files.readAllBytes(f.toPath), StandardCharsets.UTF_8)
                  ).mkString("\n")
                )
            }
            else
              Right(new String(Files.readAllBytes(f.toPath), StandardCharsets.UTF_8))
          }
        }
      }
    }

  def fetch: Cache.Fetch[F] = {
    val default     = fetchWithPolicy(None)
    val fallbackOpt = fileFallback0.map(_.fetch)
    art =>
      val f = fallbackOpt.filter(_ => art.url.startsWith("file:/")).getOrElse(default)
      f(art)
  }

  override def fetchs: Seq[Cache.Fetch[F]] =
    cachePolicies.map { policy =>
      val default = fetchWithPolicy(Some(Model.cachePolicyName(policy)))
      val fallback =
        fileFallback0.map(fallback => (art: Artifact) => fallback.fetchPerPolicy(art, policy))
      (art: Artifact) =>
        val f = fallback.filter(_ => art.url.startsWith("file:/")).getOrElse(default)
        f(art)
    }
}

object RemoteCache {

  /** The [[RemoteCache]] a [[FileCache]] defers to, given the default cache
    *
    * Only non-empty if `defaultCache` is a [[RemoteCache]] with the same location as `fileCache`.
    * The returned [[RemoteCache]] talks to the server of `defaultCache`, and has the pool, logger,
    * cache policies, and TTL of `fileCache`. `fileCache` handles `file:` URLs.
    */
  private[cache] def substituteFor[F[_]](
    fileCache: FileCache[F],
    defaultCache: Cache[Task]
  ): Option[RemoteCache[F]] =
    defaultCache match {
      case rc: RemoteCache[Task] if sameLocation(fileCache.location, rc.location) =>
        Some(
          RemoteCache[F](
            serverUrl = rc.serverUrl,
            location = fileCache.location,
            basicAuth = rc.basicAuth,
            pool = fileCache.pool,
            logger = fileCache.logger,
            cachePolicies = fileCache.cachePolicies,
            watchLenPool = rc.watchLenPool,
            fileFallback = Some(fileCache),
            ttl = fileCache.ttl
          )(fileCache.sync)
        )
      case _ =>
        None
    }

  private def sameLocation(a: File, b: File): Boolean =
    a.toPath.toAbsolutePath.normalize == b.toPath.toAbsolutePath.normalize

  final class OnGoingDownload(
    val file: File,
    val tmp: File,
    val lenFile: File,
    var artifact: Artifact
  ) {
    @volatile var started: Boolean = false
    @volatile var done: Boolean    = false
    @volatile var errored: Boolean = false
  }

}
