package coursier.publish.upload

import java.io.{ByteArrayOutputStream, InputStream, OutputStream}
import java.net.{HttpURLConnection, URL}
import java.nio.charset.StandardCharsets
import java.util.concurrent.ExecutorService

import coursier.cache.CacheUrl
import coursier.core.Authentication
import coursier.publish.upload.logger.UploadLogger
import coursier.util.Task

import scala.collection.JavaConverters._
import scala.util.Try
import scala.util.control.NonFatal

final case class HttpURLConnectionUpload(
  pool: ExecutorService,
  urlSuffix: String
) extends Upload {

  import coursier.publish.download.OkhttpDownload.TryOps

  def upload(url: String, authentication: Option[Authentication], content: Array[Byte], logger: UploadLogger, loggingIdOpt: Option[Object]): Task[Option[Upload.Error]] =
    Task.schedule(pool) {
      logger.uploading(url, loggingIdOpt, Some(content.length))

      val res = Try {
        val url0 = new URL(url + urlSuffix)

        val conn = url0.openConnection().asInstanceOf[HttpURLConnection]

        conn.setRequestMethod("PUT")

        for (auth <- authentication; (k, v) <- auth.allHttpHeaders)
          conn.setRequestProperty(k, v)

        conn.setDoOutput(true)

        conn.setRequestProperty("Content-Type", "application/octet-stream")
        conn.setRequestProperty("Content-Length", content.length.toString)

        var is: InputStream = null
        var es: InputStream = null
        var os: OutputStream = null

        try {
          os = conn.getOutputStream
          os.write(content)
          os.close()

          val code = conn.getResponseCode
          if (code == 401) {
            val realmOpt = Option(conn.getRequestProperty("WWW-Authenticate")).collect {
              case CacheUrl.BasicRealm(r) => r
            }
            Some(new Upload.Error.Unauthorized(url, realmOpt))
          } else if (code / 100 == 2)
            None
          else {
            es = conn.getErrorStream
            val buf = Array.ofDim[Byte](16384)
            val baos = new ByteArrayOutputStream
            var read = -1
            while ( {
              read = es.read(buf); read >= 0
            })
              baos.write(buf, 0, read)
            es.close()
            // FIXME Adjust charset with headers?
            val content = Try(new String(baos.toByteArray, StandardCharsets.UTF_8)).toOption.getOrElse("")
            Some(new Upload.Error.HttpError(code, conn.getHeaderFields.asScala.mapValues(_.asScala.toList).iterator.toMap, content))
          }
        } finally {
          // Trying to ensure the same connection is being re-used across requests
          // see https://docs.oracle.com/javase/8/docs/technotes/guides/net/http-keepalive.html
          try {
            if (os == null)
              os = conn.getOutputStream
            if (os != null)
              os.close()
          } catch {
            case NonFatal(_) =>
          }
          try {
            if (is == null)
              is = conn.getInputStream
            if (is != null) {
              val buf = Array.ofDim[Byte](16384)
              while (is.read(buf) > 0) {}
              is.close()
            }
          } catch {
            case NonFatal(_) =>
          }
          try {
            if (es == null)
              es = conn.getErrorStream
            if (es != null) {
              val buf = Array.ofDim[Byte](16384)
              while (es.read(buf) > 0) {}
              es.close()
            }
          } catch {
            case NonFatal(_) =>
          }
        }
      }

      logger.uploaded(url, loggingIdOpt, res.toEither.fold(e => Some(new Upload.Error.UploadError(url, e)), x => x))

      res.get
    }
}

object HttpURLConnectionUpload {
  def create(pool: ExecutorService): Upload =
    HttpURLConnectionUpload(pool, "")
  def create(pool: ExecutorService, urlSuffix: String): Upload =
    HttpURLConnectionUpload(pool, urlSuffix)
}
