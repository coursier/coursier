package coursier.publish.bintray

import java.io.FileNotFoundException

import argonaut._
import argonaut.Argonaut._
import coursier.core.Authentication
import coursier.publish.sonatype.OkHttpClientUtil
import coursier.util.Task
import okhttp3.OkHttpClient

final case class BintrayApi(
  client: OkHttpClient,
  base: String,
  authentication: Option[Authentication],
  verbosity: Int
) {

  private val clientUtil = OkHttpClientUtil(client, authentication, verbosity)

  def getRepository(subject: String, repo: String): Task[Option[Json]] =
    clientUtil.get[Json](s"$base/repos/$subject/$repo") // escaping?
      .attempt
      .flatMap {
        case Left(_: FileNotFoundException) =>
          Task.point(None)
        case Right(json) =>
          Task.point(Some(json))
        case Left(e) =>
          Task.fail(e)
      }

  def createRepository(
    subject: String,
    repo: String
  ): Task[Json] =
    clientUtil.get[Json](
      s"$base/repos/$subject/$repo",
      post = Some(
        clientUtil.postBody(
          BintrayApi.CreateRepositoryRequest(repo, "maven")
        )(BintrayApi.CreateRepositoryRequest.encoder)
      )
    )

  def createRepositoryIfNeeded(
    subject: String,
    repo: String
  ): Task[Boolean] =
    getRepository(subject, repo).flatMap {
      case None => createRepository(subject, repo).map(_ => true)
      case Some(_) => Task.point(false)
    }


  def getPackage(subject: String, repo: String, package0: String): Task[Option[Json]] =
    clientUtil.get[Json](s"$base/packages/$subject/$repo/$package0") // escaping?
      .attempt
      .flatMap {
        case Left(_: FileNotFoundException) =>
          Task.point(None)
        case Right(json) =>
          Task.point(Some(json))
        case Left(e) =>
          Task.fail(e)
      }

  def createPackage(
    subject: String,
    repo: String,
    package0: String,
    licenses: Seq[String],
    vcsUrl: String
  ): Task[Json] =
    clientUtil.get[Json](
      s"$base/packages/$subject/$repo",
      post = Some(
        clientUtil.postBody(
          BintrayApi.CreatePackageRequest(package0, licenses.toList, vcsUrl)
        )(BintrayApi.CreatePackageRequest.encoder)
      )
    )

  def createPackageIfNeeded(
    subject: String,
    repo: String,
    package0: String,
    licenses: Seq[String],
    vcsUrl: String
  ): Task[Boolean] =
    getPackage(subject, repo, package0).flatMap {
      case None => createPackage(subject, repo, package0, licenses, vcsUrl).map(_ => true)
      case Some(_) => Task.point(false)
    }

}

object BintrayApi {

  private final case class CreatePackageRequest(
    name: String,
    licenses: List[String],
    vcs_url: String
  )

  private object CreatePackageRequest {
    import argonaut.ArgonautShapeless._
    implicit val encoder = EncodeJson.of[CreatePackageRequest]
  }

  private final case class CreateRepositoryRequest(
    name: String,
    `type`: String
  )

  private object CreateRepositoryRequest {
    import argonaut.ArgonautShapeless._
    implicit val encoder = EncodeJson.of[CreateRepositoryRequest]
  }

}
