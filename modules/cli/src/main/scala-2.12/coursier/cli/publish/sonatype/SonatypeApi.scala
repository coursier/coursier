package coursier.cli.publish.sonatype

import java.nio.charset.StandardCharsets

import argonaut._
import argonaut.Argonaut._
import com.squareup.okhttp.{MediaType, OkHttpClient, Request, RequestBody}
import coursier.Cache
import coursier.core.Authentication
import coursier.util.Task

import scala.util.Try

final case class SonatypeApi(
  client: OkHttpClient,
  base: String,
  authentication: Option[Authentication],
  verbosity: Int,
  retryOnTimeout: Int = 3
) {

  // vaguely inspired by https://github.com/lihaoyi/mill/blob/7b4ced648ecd9b79b3a16d67552f0bb69f4dd543/scalalib/src/mill/scalalib/publish/SonatypeHttpApi.scala

  import SonatypeApi._

  private def postBody[B: EncodeJson](content: B): RequestBody =
    RequestBody.create(
      SonatypeApi.mediaType,
      Json.obj("data" -> EncodeJson.of[B].apply(content)).nospaces.getBytes(StandardCharsets.UTF_8)
    )

  private def get[T: DecodeJson](url: String, post: Option[RequestBody] = None): Task[T] = {

    val request = {
      val b = new Request.Builder().url(url)
      for (body <- post)
        b.post(body)

      // Handling this ourselves rather than via client.setAuthenticator / com.squareup.okhttp.Authenticator
      for (auth <- authentication)
        b.addHeader("Authorization", "Basic " + Cache.basicAuthenticationEncode(auth.user, auth.password))

      b.addHeader("Accept", "application/json,application/vnd.siesta-error-v1+json,application/vnd.siesta-validation-errors-v1+json")

      b.build()
    }

    val t = Task.delay {
      if (verbosity >= 1)
        Console.err.println(s"Getting $url")
      val resp = client.newCall(request).execute()
      if (verbosity >= 1)
        Console.err.println(s"Done: $url")

      if (resp.isSuccessful)
        resp.body().string().decodeEither(Response.decode[T]) match {
          case Left(e) =>
            Task.fail(new Exception(s"Received invalid response from $url: $e"))
          case Right(t) =>
            Task.point(t.data)
        }
      else
        Task.fail(new Exception(s"Failed to get $url (http status: ${resp.code()}, response: ${Try(resp.body().string()).getOrElse("")})"))
    }

    t.flatMap(identity)
  }

  private def withRetry[T](task: Int => Task[T]): Task[T] = {

    def helper(attempt: Int): Task[T] =
      task(attempt).attempt.flatMap {
        case Left(_: java.net.SocketTimeoutException) if attempt + 1 < retryOnTimeout =>
          helper(attempt + 1)
        case other =>
          Task.fromEither(other)
      }

    helper(0)
  }

  def listProfiles(logger: SonatypeLogger = SonatypeLogger.nop): Task[Seq[SonatypeApi.Profile]] = {

    def before(attempt: Int) = Task.delay {
      logger.listingProfiles(attempt, retryOnTimeout)
    }

    def after(errorOpt: Option[Throwable]) = Task.delay {
      logger.listedProfiles(errorOpt)
    }

    // for w/e reasons, Profiles.Profile.decode isn't implicitly picked
    val task = get(s"$base/staging/profiles")(DecodeJson.ListDecodeJson(Profiles.Profile.decode))
      .map(_.map(_.profile))

    withRetry { attempt =>
      for {
        _ <- before(attempt)
        a <- task.attempt
        _ <- after(a.left.toOption)
        res <- Task.fromEither(a)
      } yield res
    }
  }

  def rawListProfiles(): Task[Json] =
    get[Json](s"$base/staging/profiles")

  def createStagingRepository(profile: Profile, description: String): Task[String] =
    get(
      s"${profile.uri}/start",
      post = Some(postBody(StartRequest(description))(StartRequest.encoder))
    )(StartResponse.decoder).map { r =>
      r.stagedRepositoryId
    }

  def rawCreateStagingRepository(profile: Profile, description: String): Task[Json] =
    get[Json](
      s"${profile.uri}/start",
      post = Some(postBody(StartRequest(description))(StartRequest.encoder))
    )

}

object SonatypeApi {

  final case class Profile(
    id: String,
    name: String,
    uri: String
  )


  private val mediaType = MediaType.parse("application/json")

  private final case class Response[T](data: T)

  private object Response {
    import argonaut.ArgonautShapeless._
    implicit def decode[T: DecodeJson] = DecodeJson.of[Response[T]]
  }

  private object Profiles {

    final case class Profile(
      id: String,
      name: String,
      resourceURI: String
    ) {
      def profile =
        SonatypeApi.Profile(
          id,
          name,
          resourceURI
        )
    }

    object Profile {
      import argonaut.ArgonautShapeless._
      implicit val decode = DecodeJson.of[Profile]
    }
  }

  private final case class StartRequest(description: String)
  private object StartRequest {
    import argonaut.ArgonautShapeless._
    implicit val encoder = EncodeJson.of[StartRequest]
  }
  private final case class StartResponse(stagedRepositoryId: String)
  private object StartResponse {
    import argonaut.ArgonautShapeless._
    implicit val decoder = DecodeJson.of[StartResponse]
  }

}
