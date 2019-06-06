package coursier.publish.sonatype

import java.nio.charset.StandardCharsets

import argonaut._
import argonaut.Argonaut._
import coursier.cache.CacheUrl
import coursier.core.Authentication
import coursier.publish.sonatype.logger.SonatypeLogger
import coursier.util.Task
import okhttp3.{MediaType, OkHttpClient, Request, RequestBody}

import scala.util.Try

final case class SonatypeApi(
  client: OkHttpClient,
  base: String,
  authentication: Option[Authentication],
  verbosity: Int,
  retryOnTimeout: Int = 3
) {

  // vaguely inspired by https://github.com/lihaoyi/mill/blob/7b4ced648ecd9b79b3a16d67552f0bb69f4dd543/scalalib/src/mill/scalalib/publish/SonatypeHttpApi.scala
  // and https://github.com/xerial/sbt-sonatype/blob/583db138df2b0e7bbe58717103f2c9874fca2a74/src/main/scala/xerial/sbt/Sonatype.scala

  import SonatypeApi._

  private def request(url: String, post: Option[RequestBody] = None) = {
    val b = new Request.Builder().url(url)
    for (body <- post)
      b.post(body)

    // Handling this ourselves rather than via client.setAuthenticator / com.squareup.okhttp.Authenticator
    for (auth <- authentication)
      b.addHeader("Authorization", "Basic " + CacheUrl.basicAuthenticationEncode(auth.user, auth.password))

    b.addHeader("Accept", "application/json,application/vnd.siesta-error-v1+json,application/vnd.siesta-validation-errors-v1+json")

    b.build()
  }

  private def postBody[B: EncodeJson](content: B): RequestBody =
    RequestBody.create(
      SonatypeApi.mediaType,
      Json.obj("data" -> EncodeJson.of[B].apply(content)).nospaces.getBytes(StandardCharsets.UTF_8)
    )

  private def create(url: String, post: Option[RequestBody] = None): Task[Unit] = {

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

  private def get[T: DecodeJson](url: String, post: Option[RequestBody] = None, nested: Boolean = true): Task[T] = {

    val t = Task.delay {
      if (verbosity >= 1)
        Console.err.println(s"Getting $url")
      val resp = client.newCall(request(url, post)).execute()
      if (verbosity >= 1)
        Console.err.println(s"Done: $url")

      if (resp.isSuccessful) {
        if (nested)
          resp.body().string().decodeEither(Response.decode[T]) match {
            case Left(e) =>
              Task.fail(new Exception(s"Received invalid response from $url: $e"))
            case Right(t) =>
              Task.point(t.data)
          }
        else
          resp.body().string().decodeEither[T] match {
            case Left(e) =>
              Task.fail(new Exception(s"Received invalid response from $url: $e"))
            case Right(t) =>
              Task.point(t)
          }
      } else
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

  def decodeListProfilesResponse(json: Json): Either[Exception, Seq[SonatypeApi.Profile]] =
    json.as(DecodeJson.ListDecodeJson(Profiles.Profile.decode)).toEither match {
      case Left(e) => Left(new Exception(s"Error decoding response: $e"))
      case Right(l) => Right(l.map(_.profile))
    }

  def listProfileRepositories(profileIdOpt: Option[String]): Task[Seq[SonatypeApi.Repository]] =
    get(s"$base/staging/profile_repositories" + profileIdOpt.fold("")("/" + _))(DecodeJson.ListDecodeJson(RepositoryResponse.decoder))
      .map(_.map(_.repository))

  def rawListProfileRepositories(profileIdOpt: Option[String]): Task[Json] =
    get[Json](s"$base/staging/profile_repositories" + profileIdOpt.fold("")("/" + _))

  def decodeListProfileRepositoriesResponse(json: Json): Either[Exception, Seq[SonatypeApi.Repository]] =
    json.as(DecodeJson.ListDecodeJson(RepositoryResponse.decoder)).toEither match {
      case Left(e) => Left(new Exception(s"Error decoding response: $e"))
      case Right(l) => Right(l.map(_.repository))
    }

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

  private def stagedRepoAction(action: String, profile: Profile, repositoryId: String, description: String): Task[Unit] =
    create(
      s"${profile.uri}/$action",
      post = Some(postBody(StagedRepositoryRequest(description, repositoryId))(StagedRepositoryRequest.encoder))
    )

  def sendCloseStagingRepositoryRequest(profile: Profile, repositoryId: String, description: String): Task[Unit] =
    stagedRepoAction("finish", profile, repositoryId, description)

  def sendPromoteStagingRepositoryRequest(profile: Profile, repositoryId: String, description: String): Task[Unit] =
    stagedRepoAction("promote", profile, repositoryId, description)

  def sendDropStagingRepositoryRequest(profile: Profile, repositoryId: String, description: String): Task[Unit] =
    stagedRepoAction("drop", profile, repositoryId, description)

  def lastActivity(repositoryId: String, action: String) =
    get[List[Json]](s"$base/staging/repository/$repositoryId/activity", nested = false).map { l =>
      l.filter { json =>
        json.field("name").flatMap(_.string).contains(action)
      }.lastOption
    }

}

object SonatypeApi {

  final case class Profile(
    id: String,
    name: String,
    uri: String
  )

  final case class Repository(
    profileId: String,
    profileName: String,
    id: String,
    `type`: String
  )


  def activityErrored(activity: Json): Either[List[String], Unit] =
    Activity.decoder.decodeJson(activity).toEither match {
      case Left(e) => ???
      case Right(a) =>
        val errors = a.events.filter(_.severity >= 1).map(_.name)
        if (errors.isEmpty)
          Right(())
        else
          Left(errors)
    }

  // same kind of check as sbt-sonatype
  def repositoryClosed(activity: Json, repoId: String): Boolean =
    Activity.decoder.decodeJson(activity).toEither match {
      case Left(_) => ???
      case Right(a) => a.events.exists(e => e.name == "repositoryClosed" && e.properties.exists(p => p.name == "id" && p.value == repoId))
    }
  def repositoryPromoted(activity: Json, repoId: String): Boolean =
    Activity.decoder.decodeJson(activity).toEither match {
      case Left(_) => ???
      case Right(a) => a.events.exists(e => e.name == "repositoryReleased" && e.properties.exists(p => p.name == "id" && p.value == repoId))
    }


  private final case class Activity(name: String, events: List[Activity.Event])

  private object Activity {
    import argonaut.ArgonautShapeless._
    final case class Event(name: String, severity: Int, properties: List[Property])
    final case class Property(name: String, value: String)
    implicit val decoder = DecodeJson.of[Activity]
  }


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

  private final case class RepositoryResponse(
    profileId: String,
    profileName: String,
    repositoryId: String,
    `type`: String
  ) {
    def repository: Repository =
      Repository(
        profileId,
        profileName,
        repositoryId,
        `type`
      )
  }
  private object RepositoryResponse {
    import argonaut.ArgonautShapeless._
    implicit val decoder = DecodeJson.of[RepositoryResponse]
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

  private final case class StagedRepositoryRequest(
    description: String,
    stagedRepositoryId: String
  )
  private object StagedRepositoryRequest {
    import argonaut.ArgonautShapeless._
    implicit val encoder = EncodeJson.of[StagedRepositoryRequest]
  }

}
