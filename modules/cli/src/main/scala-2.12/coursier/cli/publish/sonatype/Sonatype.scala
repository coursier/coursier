package coursier.cli.publish.sonatype

import java.util.concurrent.TimeUnit

import caseapp._
import com.squareup.okhttp.OkHttpClient
import coursier.util.Task

import scala.concurrent.duration.Duration
import scala.concurrent.{Await, ExecutionContext}

object Sonatype extends CaseApp[SonatypeOptions] {
  def run(options: SonatypeOptions, remainingArgs: RemainingArgs) = {

    if (remainingArgs.all.nonEmpty)
      sys.error(s"unexpected arguments: ${remainingArgs.all.mkString(", ")}")

    val params = SonatypeParams(options).toEither match {
      case Left(errs) =>
        errs.toList.foreach(println)
        sys.exit(1)
      case Right(p) => p
    }

    val client = new OkHttpClient
    // Sonatype can be quite slow…
    client.setReadTimeout(60L, TimeUnit.SECONDS)

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

    val maybeListProfiles = {
      if (params.listProfiles) {
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
      } else if (params.needListProfiles)
        api.listProfiles().map(Some(_))
      else
        Task.point(None)
    }

    def maybeList(profileIdOpt: Option[String]): Task[Option[Seq[SonatypeApi.Repository]]] =
      if (params.list) {
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
              for (r <- repositories) {
                val extra = profileIdOpt.fold(s", profile: ${r.profileName}")(_ => "")
                println(s"  ${r.id} (${r.`type`}" + extra + ")")
              }
            }
          } yield Some(repositories)
      } else if (params.needListRepositories)
        api.listProfileRepositories(profileIdOpt).map(Some(_))
      else
        Task.point(None)

    def maybeCreate(profile: SonatypeApi.Profile) =
      if (params.create) {
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
      } else
        Task.point(())

    def maybeClose(profile: SonatypeApi.Profile, repoId: String) =
      if (params.close)
        for {
          _ <- api.sendCloseStagingRepositoryRequest(profile, repoId, params.description.getOrElse(""))
          _ <- Task.delay(println(s"Closed repository $repoId"))
        } yield ()
      else
        Task.point(())

    def maybePromote(profile: SonatypeApi.Profile, repoId: String) =
      if (params.promote)
        for {
          _ <- api.sendPromoteStagingRepositoryRequest(profile, repoId, params.description.getOrElse(""))
          _ <- Task.delay(println(s"Promoted repository $repoId"))
        } yield ()
      else
        Task.point(())

    def maybeDrop(profile: SonatypeApi.Profile, repoId: String) =
      if (params.drop)
        for {
          _ <- api.sendDropStagingRepositoryRequest(profile, repoId, params.description.getOrElse(""))
          _ <- Task.delay(println(s"Dropped repository $repoId"))
        } yield ()
      else
        Task.point(())

    val t = for {
      profilesOpt <- maybeListProfiles
      repositoriesOpt <- params.profileIdOpt match {
        case Some(id) => maybeList(Some(id))
        case None =>
          params.profileNameOpt match {
            case Some(name) =>
              profileTask(profilesOpt.getOrElse(Nil), Right(name)).flatMap(p => maybeList(Some(p.id)))
            case None =>
              maybeList(None)
          }
      }
      profileOpt <- {
        if (params.create || params.close || params.promote || params.drop)
          params.profileIdOpt match {
            case Some(id) =>
              profileTask(profilesOpt.getOrElse(Nil), Left(id)).map(Some(_))
            case None =>
              params.profileNameOpt match {
                case Some(name) =>
                  profileTask(profilesOpt.getOrElse(Nil), Right(name)).map(Some(_))
                case None =>
                  Task.point {
                    params.repositoryIdOpt.flatMap { repoId =>
                      for {
                        repositories <- repositoriesOpt
                        r <- repositories.find(_.id == repoId)
                        p <- profilesOpt.getOrElse(Nil).find(_.id == r.profileId)
                      } yield p
                    }
                  }
              }
          }
        else
          Task.point(None)
      }
      _ <- {
        profileOpt match {
          case None =>
            if (params.create)
              throw new Exception("No profile")
            Task.point(())
          case Some(profile) => maybeCreate(profile)
        }
      }
      _ <- {
        (profileOpt, params.repositoryIdOpt) match {
          case (Some(profile), Some(repoId)) =>
            for {
              _ <- maybeClose(profile, repoId)
              _ <- maybePromote(profile, repoId)
              _ <- maybeDrop(profile, repoId)
            } yield ()
          case _ =>
            if (params.close || params.promote || params.drop)
              throw new Exception(s"No profile or repo (repositoriesOpt: $repositoriesOpt, profilesOpt: $profilesOpt)")
            Task.point(())
        }
      }
    } yield ()

    Await.result(t.future()(ExecutionContext.global), Duration.Inf)
  }
}
