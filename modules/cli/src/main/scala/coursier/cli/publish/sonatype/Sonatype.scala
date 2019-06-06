package coursier.cli.publish.sonatype

import java.util.concurrent.TimeUnit

import caseapp._
import coursier.publish.sonatype.SonatypeApi
import coursier.util.Task
import okhttp3.OkHttpClient

import scala.concurrent.duration.Duration
import scala.concurrent.{Await, ExecutionContext}

object Sonatype extends CaseApp[SonatypeOptions] {

  def listProfiles(params: SonatypeParams, api: SonatypeApi): Task[Option[Seq[SonatypeApi.Profile]]] =
    if (params.raw)
      for {
        json <- api.rawListProfiles()
        _  <- Task.delay {
          println(json.spaces2)
        }
        profiles <- Task.fromEither(api.decodeListProfilesResponse(json))
      } yield Some(profiles)
    else
      for {
        profiles <- api.listProfiles()
        _  <- Task.delay {
          for (p <- profiles)
            println(s"Profile ${p.name}\n  id: ${p.id}\n  URL: ${p.uri}")
        }
      } yield Some(profiles)

  def list(params: SonatypeParams, api: SonatypeApi, profileIdOpt: Option[String]): Task[Option[Seq[SonatypeApi.Repository]]] =
    if (params.raw)
      for {
        json <- api.rawListProfileRepositories(profileIdOpt)
        _ <- Task.delay(println(json.spaces2))
        repositories <- Task.fromEither(api.decodeListProfileRepositoriesResponse(json))
      } yield Some(repositories)
    else
      for {
        repositories <- api.listProfileRepositories(profileIdOpt)
        _ <- Task.delay {
          println("Repositories" + profileIdOpt.fold("")(p => s" of profile $p"))
          for (r <- repositories if !params.cleanList || !r.id.startsWith("central_bundles-")) {
            val extra = profileIdOpt.fold(s", profile: ${r.profileName}")(_ => "")
            println(s"  ${r.id} (${r.`type`}" + extra + ")")
          }
        }
      } yield Some(repositories)

  def create(params: SonatypeParams, api: SonatypeApi, profile: SonatypeApi.Profile): Task[Unit] =
    if (params.raw)
      for {
        json <- api.rawCreateStagingRepository(profile, params.description.getOrElse(""))
        _ <- Task.delay(println(json.spaces2))
      } yield ()
    else
      for {
        repoId <- api.createStagingRepository(profile, params.description.getOrElse(""))
        _ <- Task.delay(println(s"Created repository $repoId"))
      } yield ()

  def close(params: SonatypeParams, api: SonatypeApi, profile: SonatypeApi.Profile, repoId: String): Task[Unit] =
    for {
      _ <- api.sendCloseStagingRepositoryRequest(profile, repoId, params.description.getOrElse(""))
      _ <- Task.delay(println(s"Closed repository $repoId"))
    } yield ()

  def promote(params: SonatypeParams, api: SonatypeApi, profile: SonatypeApi.Profile, repoId: String): Task[Unit] =
    for {
      _ <- api.sendPromoteStagingRepositoryRequest(profile, repoId, params.description.getOrElse(""))
      _ <- Task.delay(println(s"Promoted repository $repoId"))
    } yield ()

  def drop(params: SonatypeParams, api: SonatypeApi, profile: SonatypeApi.Profile, repoId: String): Task[Unit] =
    for {
      _ <- api.sendDropStagingRepositoryRequest(profile, repoId, params.description.getOrElse(""))
      _ <- Task.delay(println(s"Dropped repository $repoId"))
    } yield ()


  def maybeListProfiles(params: SonatypeParams, api: SonatypeApi): Task[Option[Seq[SonatypeApi.Profile]]] =
    if (params.listProfiles)
      listProfiles(params, api)
    else if (params.needListProfiles)
      api.listProfiles().map(Some(_))
    else
      Task.point(None)

  def maybeList(params: SonatypeParams, api: SonatypeApi, profileIdOpt: Option[String]): Task[Option[Seq[SonatypeApi.Repository]]] =
    if (params.list)
      list(params, api, profileIdOpt)
    else if (params.needListRepositories)
      api.listProfileRepositories(profileIdOpt).map(Some(_))
    else
      Task.point(None)

  def maybeCreate(params: SonatypeParams, api: SonatypeApi, profile: SonatypeApi.Profile): Task[Unit] =
    if (params.create)
      create(params, api, profile)
    else
      Task.point(())

  def maybeClose(params: SonatypeParams, api: SonatypeApi, profile: SonatypeApi.Profile, repoId: String): Task[Unit] =
    if (params.close)
      close(params, api, profile, repoId)
    else
      Task.point(())

  def maybePromote(params: SonatypeParams, api: SonatypeApi, profile: SonatypeApi.Profile, repoId: String): Task[Unit] =
    if (params.promote)
      promote(params, api, profile, repoId)
    else
      Task.point(())

  def maybeDrop(params: SonatypeParams, api: SonatypeApi, profile: SonatypeApi.Profile, repoId: String): Task[Unit] =
    if (params.drop)
      drop(params, api, profile, repoId)
    else
      Task.point(())


  def run(options: SonatypeOptions, remainingArgs: RemainingArgs): Unit = {

    if (remainingArgs.all.nonEmpty)
      sys.error(s"unexpected arguments: ${remainingArgs.all.mkString(", ")}")

    val params = SonatypeParams(options).toEither match {
      case Left(errs) =>
        errs.toList.foreach(println)
        sys.exit(1)
      case Right(p) => p
    }

    val client = new OkHttpClient.Builder()
      // Sonatype can be quite slow…
      .readTimeout(60L, TimeUnit.SECONDS)
      .build()

    val api = SonatypeApi(client, params.base, params.authentication, verbosity = params.verbosity)

    def profileTask(profiles: Seq[SonatypeApi.Profile], idOrName: Either[String, String]) = {

      val profileOpt = idOrName match {
        case Left(id) => profiles.find(_.id == id)
        case Right(name) => profiles.find(_.name == name)
      }

      profileOpt match {
        case None => Task.fail(new Exception(s"Profile ${idOrName.merge} not found"))
        case Some(p) => Task.delay {
          if (idOrName.isRight)
            Console.err.println(s"Profile id of ${idOrName.merge}: ${p.id}")
          p
        }
      }
    }

    val t = for {
      profiles <- maybeListProfiles(params, api).map(_.getOrElse(Nil))

      repositoriesOpt <- {

        val profileIdOptTask = params.profileIdOpt match {
          case Some(id) => Task.point(Some(id))
          case None =>
            params.profileNameOpt match {
              case Some(name) =>
                profileTask(profiles, Right(name))
                  .map(p => Some(p.id))
              case None =>
                Task.point(None)
            }
        }

        profileIdOptTask
          .flatMap(maybeList(params, api, _))
      }

      profileOpt <- {

        val proceed = params.create || params.close || params.promote || params.drop

        if (proceed)
          (params.profileIdOpt, params.profileNameOpt) match {
            case (Some(id), _) =>
              profileTask(profiles, Left(id))
                .map(Some(_))

            case (None, Some(name)) =>
              profileTask(profiles, Right(name))
                .map(Some(_))

            case (None, None) =>

              val profileOpt = for {
                repoId <- params.repositoryIdOpt
                repositories <- repositoriesOpt
                r <- repositories.find(_.id == repoId)
                p <- profiles.find(_.id == r.profileId)
              } yield p

              Task.point(profileOpt)
          }
        else
          Task.point(None)
      }
      _ <- {
        profileOpt match {
          case None =>
            if (params.create)
              Task.fail(new Exception("No profile"))
            else
              Task.point(())
          case Some(profile) =>
            maybeCreate(params, api, profile)
        }
      }
      _ <- {
        (profileOpt, params.repositoryIdOpt) match {
          case (Some(profile), Some(repoId)) =>
            for {
              _ <- maybeClose(params, api, profile, repoId)
              _ <- maybePromote(params, api, profile, repoId)
              _ <- maybeDrop(params, api, profile, repoId)
            } yield ()
          case _ =>
            if (params.close || params.promote || params.drop)
              Task.fail(new Exception(s"No profile or repo (repositoriesOpt: $repositoriesOpt, profiles: ${profiles.mkString(", ")})"))
            else
              Task.point(())
        }
      }
    } yield ()

    Await.result(t.future()(ExecutionContext.global), Duration.Inf)
  }
}
