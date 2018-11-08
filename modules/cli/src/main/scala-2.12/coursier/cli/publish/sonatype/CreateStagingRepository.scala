package coursier.cli.publish.sonatype

import java.util.concurrent.TimeUnit

import caseapp._
import com.squareup.okhttp.OkHttpClient
import coursier.cli.publish.sonatype.options.CreateStagingRepositoryOptions
import coursier.cli.publish.sonatype.params.CreateStagingRepositoryParams
import coursier.util.Task

import scala.concurrent.duration.Duration
import scala.concurrent.{Await, ExecutionContext}

object CreateStagingRepository extends CaseApp[CreateStagingRepositoryOptions] {
  def run(options: CreateStagingRepositoryOptions, remainingArgs: RemainingArgs) = {

    if (remainingArgs.all.nonEmpty)
      sys.error(s"unexpected arguments: ${remainingArgs.all.mkString(", ")}")

    val params = CreateStagingRepositoryParams(options).toEither match {
      case Left(errs) =>
        errs.toList.foreach(println)
        sys.exit(1)
      case Right(p) => p
    }

    val client = new OkHttpClient
    client.setReadTimeout(30L, TimeUnit.SECONDS)

    val api = SonatypeApi(client, params.sonatype.base, params.sonatype.authentication, verbosity = 2)

    val t = for {
      profiles <- api.listProfiles()
      profileOpt = params.profile match {
        case Left(id) => profiles.find(_.id == id)
        case Right(name) => profiles.find(_.name == name)
      }
      profile <- profileOpt match {
        case None => Task.fail(new Exception(s"Profile ${params.profile.merge} not found"))
        case Some(p) => Task.point(p)
      }
      _ <- {
        if (params.raw)
          for {
            json <- api.rawCreateStagingRepository(profile, params.description.getOrElse(""))
            _  <- Task.delay {
              println(json.spaces2)
            }
          } yield ()
        else
          for {
            repoId <- api.createStagingRepository(profile, params.description.getOrElse(""))
            _  <- Task.delay {
              println(s"Created repository $repoId")
            }
          } yield ()
      }
    } yield ()

    Await.result(t.future()(ExecutionContext.global), Duration.Inf)
  }
}
