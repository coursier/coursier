package coursier.publish.sonatype

import java.util.concurrent.ScheduledExecutorService

import argonaut._
import argonaut.Argonaut._
import coursier.core.Authentication
import coursier.publish.sonatype.logger.SonatypeLogger
import coursier.util.Task
import okhttp3.{MediaType, OkHttpClient, RequestBody}

import scala.concurrent.duration.Duration

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

  private def postBody[B: EncodeJson](content: B): RequestBody =
    clientUtil.postBody(Json.obj("data" -> EncodeJson.of[B].apply(content)))

  private def get[T: DecodeJson](url: String, post: Option[RequestBody] = None, nested: Boolean = true): Task[T] =
    clientUtil.get(url, post, nested)(Response.decode[T]).map(_.data)

  private val clientUtil = OkHttpClientUtil(client, authentication, verbosity)

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
    clientUtil.create(
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

  def waitForStatus(
    profileId: String,
    repositoryId: String,
    status: String,
    maxAttempt: Int,
    initialDelay: Duration,
    backoffFactor: Double,
    es: ScheduledExecutorService
  ): Task[Unit] = {

    // TODO Stop early in case of error (which statuses exactly???)

    def task(attempt: Int, nextDelay: Duration, totalDelay: Duration): Task[Unit] =
      listProfileRepositories(Some(profileId)).flatMap { l =>
        l.find(_.id == repositoryId) match {
          case None =>
            Task.fail(new Exception(s"Repository $repositoryId not found"))
          case Some(repo) =>
            // TODO Use logger for that
            System.err.println(s"Repository $repositoryId has status ${repo.`type`}")
            repo.`type` match {
              case `status` =>
                Task.point(())
              case other =>
                if (attempt < maxAttempt)
                  task(attempt + 1, backoffFactor * nextDelay, totalDelay + nextDelay)
                    .schedule(nextDelay, es)
                else
                  // FIXME totalDelay doesn't include the duration of the requests themselves (only the time between)
                  Task.fail(new Exception(s"Repository $repositoryId in state $other after $totalDelay"))
            }
        }
      }

    task(1, initialDelay, Duration.Zero)
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
