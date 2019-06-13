package coursier.publish.sonatype

import java.io.FileNotFoundException
import java.nio.charset.StandardCharsets

import argonaut.{DecodeJson, EncodeJson}
import argonaut.Argonaut._
import coursier.cache.CacheUrl
import coursier.core.Authentication
import coursier.util.Task
import okhttp3.{MediaType, OkHttpClient, Request, RequestBody}

import scala.collection.JavaConverters._
import scala.util.Try

final case class OkHttpClientUtil(
  client: OkHttpClient,
  authentication: Option[Authentication],
  verbosity: Int
) {

  private def request(url: String, post: Option[RequestBody] = None): Request = {
    val b = new Request.Builder().url(url)
    for (body <- post)
      b.post(body)

    // Handling this ourselves rather than via client.setAuthenticator / com.squareup.okhttp.Authenticator
    for (auth <- authentication)
      b.addHeader("Authorization", "Basic " + CacheUrl.basicAuthenticationEncode(auth.user, auth.password))

    // ???
    b.addHeader("Accept", "application/json,application/vnd.siesta-error-v1+json,application/vnd.siesta-validation-errors-v1+json")

    val r = b.build()

    if (verbosity >= 2) {
      val m = r.headers().toMultimap.asScala.mapValues(_.asScala.toVector)
      for ((k, l) <- m; v <- l) {
        System.err.println(s"$k: $v")
      }
    }

    r
  }

  def postBody[B: EncodeJson](content: B): RequestBody =
    RequestBody.create(
      OkHttpClientUtil.mediaType,
      EncodeJson.of[B].apply(content).nospaces.getBytes(StandardCharsets.UTF_8)
    )

  def create(url: String, post: Option[RequestBody] = None): Task[Unit] = {

    val t = Task.delay {
      if (verbosity >= 1)
        Console.err.println(s"Getting $url")
      val resp = client.newCall(request(url, post)).execute()
      if (verbosity >= 1)
        Console.err.println(s"Done: $url")

      if (resp.code() == 201)
        Task.point(())
      else
        Task.fail(new Exception(s"Failed to get $url (http status: ${resp.code()}, response: ${Try(resp.body().string()).getOrElse("")})"))
    }

    t.flatMap(identity)
  }

  def get[T: DecodeJson](url: String, post: Option[RequestBody] = None, nested: Boolean = true): Task[T] = {

    val t = Task.delay {
      if (verbosity >= 1)
        Console.err.println(s"Getting $url")
      if (verbosity >= 2) {
        post.foreach { b =>
          val buf = new okio.Buffer
          b.writeTo(buf)
          System.err.println("Sending " + buf)
        }
      }
      val resp = client.newCall(request(url, post)).execute()
      if (verbosity >= 1)
        Console.err.println(s"Done: $url")

      if (resp.isSuccessful) {
        if (nested)
          resp.body().string().decodeEither(DecodeJson.of[T]) match {
            case Left(e) =>
              Task.fail(new Exception(s"Received invalid response from $url: $e"))
            case Right(t) =>
              Task.point(t)
          }
        else
          resp.body().string().decodeEither[T] match {
            case Left(e) =>
              Task.fail(new Exception(s"Received invalid response from $url: $e"))
            case Right(t) =>
              Task.point(t)
          }
      } else {
        val msg = s"Failed to get $url (http status: ${resp.code()}, response: ${Try(resp.body().string()).getOrElse("")})"
        val notFound = resp.code() / 100 == 4
        if (notFound)
          Task.fail(new FileNotFoundException(msg))
        else
          Task.fail(new Exception(msg))
      }
    }

    t.flatMap(identity)
  }

}

object OkHttpClientUtil {

  private val mediaType = MediaType.parse("application/json")

}
