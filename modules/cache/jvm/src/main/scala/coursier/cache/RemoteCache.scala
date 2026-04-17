package coursier.cache

import com.github.plokhotnyuk.jsoniter_scala.core._
import coursier.cache.server.Model.{Artifact => ModelArtifact, _}
import coursier.paths.CachePath
import coursier.util.{Artifact, EitherT, Sync, Task}
import dataclass.data

import java.io.{ByteArrayOutputStream, File}
import java.net.{HttpURLConnection, URI, URL}
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.util.Base64
import java.util.concurrent.{ConcurrentHashMap, ExecutorService}

import scala.cli.config.Secret
import scala.concurrent.{ExecutionContext, ExecutionContextExecutorService}
import scala.util.Try

@data class RemoteCache[F[_]: Sync](
  serverUrl: String,
  location: File,
  basicAuth: Option[Secret[String]] = None, // user:password
  pool: ExecutorService = CacheDefaults.pool,
  logger: CacheLogger = CacheLogger.nop,
  cachePolicies: Seq[CachePolicy] = CacheDefaults.cachePolicies,
  watchLenPool: ExecutorService = CacheDefaults.watchLenPool,
  fileFallback: Option[FileCache[F]] = None
) extends Cache[F] with Cache.HasLocation with Cache.HasExecutionContext
    with Cache.WithLogger[F, RemoteCache[F]] with Cache.Default[F] {

  lazy val ec: ExecutionContextExecutorService =
    ExecutionContext.fromExecutorService(pool)

  private val onGoing = new ConcurrentHashMap[String, RemoteCache.OnGoingDownload]

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
          val request = GetRequest(ModelArtifact.fromArtifact(artifact), cachePolicy)
          val body    = writeToArray(request)

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

  def file(artifact: Artifact): EitherT[F, ArtifactError, File] =
    fileFallback.filter(_ => artifact.url.startsWith("file:/")) match {
      case Some(fallback) =>
        fallback.file(artifact)
      case None =>
        fileWithPolicy(artifact, None)
    }

  private def fetchWithPolicy(cachePolicy: Option[String]): Cache.Fetch[F] =
    artifact =>
      fileWithPolicy(artifact, cachePolicy).leftMap(_.describe).flatMap { f =>
        EitherT {
          Sync[F].schedule(pool) {
            if (!f.exists())
              Left(s"File not found: $f")
            else
              Right(new String(Files.readAllBytes(f.toPath), StandardCharsets.UTF_8))
          }
        }
      }

  def fetch: Cache.Fetch[F] = {
    val default     = fetchWithPolicy(None)
    val fallbackOpt = fileFallback.map(_.fetch)
    art =>
      val f = fallbackOpt.filter(_ => art.url.startsWith("file:/")).getOrElse(default)
      f(art)
  }

  override def fetchs: Seq[Cache.Fetch[F]] =
    cachePolicies.map { policy =>
      val default = fetchWithPolicy(Some(policy.toString))
      val fallback =
        fileFallback.map(fallback => (art: Artifact) => fallback.fetchPerPolicy(art, policy))
      (art: Artifact) =>
        val f = fallback.filter(_ => art.url.startsWith("file:/")).getOrElse(default)
        f(art)
    }
}

object RemoteCache {

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
