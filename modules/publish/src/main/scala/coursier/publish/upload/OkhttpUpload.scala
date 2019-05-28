package coursier.publish.upload

import java.util.concurrent.ExecutorService

import com.squareup.okhttp.{MediaType, OkHttpClient, Request, RequestBody}
import coursier.cache.CacheUrl
import coursier.core.Authentication
import coursier.publish.upload.logger.UploadLogger
import coursier.util.Task

import scala.collection.JavaConverters._
import scala.util.Try

final case class OkhttpUpload(client: OkHttpClient, pool: ExecutorService) extends Upload {

  import OkhttpUpload.mediaType
  import coursier.publish.download.OkhttpDownload.TryOps

  def upload(url: String, authentication: Option[Authentication], content: Array[Byte], logger: UploadLogger): Task[Option[Upload.Error]] = {

    val body = RequestBody.create(mediaType, content)

    val request = {
      val b = new Request.Builder()
        .url(url)
        .put(body)

      // Handling this ourselves rather than via client.setAuthenticator / com.squareup.okhttp.Authenticator
      for (auth <- authentication)
        b.addHeader("Authorization", "Basic " + CacheUrl.basicAuthenticationEncode(auth.user, auth.password))

      b.build()
    }

    Task.schedule(pool) {
      logger.uploading(url)

      val res = Try {
        val response = client.newCall(request).execute()

        if (response.isSuccessful)
          None
        else {
          val code = response.code()
          if (code == 401) {
            val realmOpt = Option(response.header("WWW-Authenticate")).collect {
              case CacheUrl.BasicRealm(r) => r
            }
            Some(new Upload.Error.Unauthorized(url, realmOpt))
          } else {
            val content = Try(response.body().string()).getOrElse("")
            Some(new Upload.Error.HttpError(code, response.headers().toMultimap.asScala.mapValues(_.asScala.toList).iterator.toMap, content))
          }
        }
      }

      logger.uploaded(url, res.toEither.fold(e => Some(new Upload.Error.UploadError(url, e)), x => x))

      res.get
    }
  }
}

object OkhttpUpload {
  private val mediaType = MediaType.parse("application/octet-stream")

  def create(pool: ExecutorService): Upload = {
    // Seems we can't even create / shutdown the client thread pool (via its Dispatcher)…
    OkhttpUpload(new OkHttpClient, pool)
  }
}
