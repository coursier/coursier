package coursier.cli.publish.sonatype

import java.util.concurrent.TimeUnit

import caseapp._
import com.squareup.okhttp.OkHttpClient
import coursier.cli.publish.sonatype.options.ListProfilesOptions
import coursier.cli.publish.sonatype.params.ListProfilesParams
import coursier.util.Task

import scala.concurrent.duration.Duration
import scala.concurrent.{Await, ExecutionContext}

object ListProfiles extends CaseApp[ListProfilesOptions] {
  def run(options: ListProfilesOptions, remainingArgs: RemainingArgs): Unit = {

    if (remainingArgs.all.nonEmpty)
      sys.error(s"unexpected arguments: ${remainingArgs.all.mkString(", ")}")

    val params = ListProfilesParams(options).toEither match {
      case Left(errs) =>
        errs.toList.foreach(println)
        sys.exit(1)
      case Right(p) => p
    }

    val client = new OkHttpClient
    client.setReadTimeout(30L, TimeUnit.SECONDS)

    val api = SonatypeApi(client, params.sonatype.base, params.sonatype.authentication, verbosity = 2)

    val t =
      if (params.raw)
        for {
          json <- api.rawListProfiles()
          _  <- Task.delay {
            println(json.spaces2)
          }
        } yield ()
      else
        for {
          l <- api.listProfiles()
          _  <- Task.delay {
            for (p <- l)
              println(s"Profile ${p.name}\n  id: ${p.id}\n  URL: ${p.uri}")
          }
        } yield ()

    Await.result(t.future()(ExecutionContext.global), Duration.Inf)
  }
}
