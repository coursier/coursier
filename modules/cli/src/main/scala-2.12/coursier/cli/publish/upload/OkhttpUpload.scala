package coursier.cli.publish.upload

import java.time.Instant
import java.util.concurrent.ExecutorService

import com.squareup.okhttp.internal.http.HttpDate
import com.squareup.okhttp.{MediaType, OkHttpClient, Request, RequestBody}
import coursier.Cache
import coursier.core.Authentication
import coursier.util.Task

import scala.collection.JavaConverters._
import scala.util.Try

final case class OkhttpUpload(client: OkHttpClient, pool: ExecutorService) extends Upload {

  import OkhttpUpload.mediaType

  def exists(url: String, authentication: Option[Authentication], logger: Upload.Logger): Task[Boolean] = {

    // FIXME Some duplication with upload below…

    val request = {
      val b = new Request.Builder()
        .url(url)
        .head()

      // Handling this ourselves rather than via client.setAuthenticator / com.squareup.okhttp.Authenticator
      for (auth <- authentication)
        b.addHeader("Authorization", "Basic " + Cache.basicAuthenticationEncode(auth.user, auth.password))

      b.build()
    }

    Task.schedule(pool) {
      logger.checking(url)

      val res = Try {
        val response = client.newCall(request).execute()

        if (response.isSuccessful)
          Right(true)
        else {
          val code = response.code()
          if (code == 404)
            Right(false)
          else if (code == 401) {
            val realmOpt = Option(response.header("WWW-Authenticate")).collect {
              case Cache.BasicRealm(r) => r
            }
            Left(new Upload.Error.Unauthorized(url, realmOpt))
          } else {
            val content = Try(response.body().string()).getOrElse("")
            Left(new Upload.Error.HttpError(code, response.headers().toMultimap.asScala.mapValues(_.asScala.toList).iterator.toMap, content))
          }
        }
      }.toEither.flatMap(identity)

      logger.checked(
        url,
        res.right.exists(identity),
        res.left.toOption.map(e => new Upload.Error.DownloadError(e))
      )

      Task.fromEither(res)
    }.flatMap(identity)
  }

  def downloadIfExists(url: String, authentication: Option[Authentication], logger: Upload.Logger): Task[Option[(Option[Instant], Array[Byte])]] = {

    // FIXME Some duplication with upload below…

    val request = {
      val b = new Request.Builder()
        .url(url)
        .get()

      // Handling this ourselves rather than via client.setAuthenticator / com.squareup.okhttp.Authenticator
      for (auth <- authentication)
        b.addHeader("Authorization", "Basic " + Cache.basicAuthenticationEncode(auth.user, auth.password))

      b.build()
    }

    Task.schedule(pool) {
      logger.downloadingIfExists(url)

      val res = Try {
        val response = client.newCall(request).execute()

        if (response.isSuccessful) {
          val lastModifiedOpt = Option(response.header("Last-Modified")).map { s =>
            HttpDate.parse(s).toInstant
          }
          Right(Some((lastModifiedOpt, response.body().bytes())))
        } else {
          val code = response.code()
          if (code == 404)
            Right(None)
          else if (code == 401) {
            val realmOpt = Option(response.header("WWW-Authenticate")).collect {
              case Cache.BasicRealm(r) => r
            }
            Left(new Upload.Error.Unauthorized(url, realmOpt))
          } else {
            val content = Try(response.body().string()).getOrElse("")
            Left(new Upload.Error.HttpError(code, response.headers().toMultimap.asScala.mapValues(_.asScala.toList).iterator.toMap, content))
          }
        }
      }.toEither.flatMap(identity)

      logger.downloadedIfExists(
        url,
        res.right.toOption.flatMap(_.map(_._2.length)),
        res.left.toOption.map(e => new Upload.Error.DownloadError(e))
      )

      Task.fromEither(res)
    }.flatMap(identity)
  }

  def upload(url: String, authentication: Option[Authentication], content: Array[Byte], logger: Upload.Logger): Task[Option[Upload.Error]] = {

    val body = RequestBody.create(mediaType, content)

    val request = {
      val b = new Request.Builder()
        .url(url)
        .put(body)

      // Handling this ourselves rather than via client.setAuthenticator / com.squareup.okhttp.Authenticator
      for (auth <- authentication)
        b.addHeader("Authorization", "Basic " + Cache.basicAuthenticationEncode(auth.user, auth.password))

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
              case Cache.BasicRealm(r) => r
            }
            Some(new Upload.Error.Unauthorized(url, realmOpt))
          } else {
            val content = Try(response.body().string()).getOrElse("")
            Some(new Upload.Error.HttpError(code, response.headers().toMultimap.asScala.mapValues(_.asScala.toList).iterator.toMap, content))
          }
        }
      }

      logger.uploaded(url, res.toEither.fold(e => Some(new Upload.Error.DownloadError(e)), x => x))

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
