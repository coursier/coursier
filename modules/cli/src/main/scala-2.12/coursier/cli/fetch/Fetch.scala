package coursier.cli.fetch

import java.io.{File, PrintStream}
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.util.concurrent.ExecutorService

import caseapp._
import cats.data.Validated
import coursier.cli.options.FetchOptions
import coursier.cli.params.FetchParams
import coursier.cli.resolve.{Output, ResolveException}
import coursier.util.{Schedulable, Task}

import scala.concurrent.ExecutionContext

object Fetch extends CaseApp[FetchOptions] {

  def task(
    params: FetchParams,
    pool: ExecutorService,
    args: Seq[String],
    stdout: PrintStream = System.out,
    stderr: PrintStream = System.err
  ): Task[Seq[File]] = {

    val resolveTask = coursier.cli.resolve.Resolve.task(
      params.resolve,
      pool,
      System.out,
      System.err,
      args,
      printOutput = false
    )

    val logger = params.resolve.output.logger()

    val cache = params.resolve.cache.cache[Task](pool, logger)

    for {
      t <- resolveTask
      (res, valid) = t
      _ =  {
        if (valid)
          Task.point(())
        else if (params.artifact.force)
          Task.delay {
            stderr.println("Resolution failed, trying to fetch artifacts anyway")
          }
        else
          Task.fromEither(Left(
            new ResolveException("Resolution failed")
          ))
      }
      artifacts = coursier.Fetch.artifacts(
        res,
        params.artifact.classifiers,
        params.artifact.mainArtifacts,
        params.artifact.artifactTypes
      )

      artifactFiles <- coursier.Fetch.fetchArtifacts(
        artifacts.map(_._3).distinct,
        cache,
        logger
        // FIXME Allow to adjust retryCount via CLI args?
      )

      _ <- {
        params.jsonOutputOpt match {
          case Some(output) =>
            Task.delay {
              val report = JsonOutput.report(
                res,
                artifacts,
                artifactFiles,
                params.artifact.classifiers,
                params.resolve.output.verbosity >= 1
              )

              Files.write(output, report.getBytes(StandardCharsets.UTF_8))
            }
          case None =>
            Task.point(())
        }
      }
    } yield artifactFiles.map(_._2)
  }

  def run(options: FetchOptions, args: RemainingArgs): Unit =
    FetchParams(options) match {
      case Validated.Invalid(errors) =>
        for (err <- errors.toList)
          Output.errPrintln(err)
        sys.exit(1)
      case Validated.Valid(params) =>

        val pool = Schedulable.fixedThreadPool(params.resolve.cache.parallel)
        val ec = ExecutionContext.fromExecutorService(pool)

        val t = task(params, pool, args.all)

        t.attempt.unsafeRun()(ec) match {
          case Left(e: ResolveException) =>
            Output.errPrintln(e.message)
            sys.exit(1)
          case Left(e: coursier.Fetch.DownloadingArtifactException) =>
            Output.errPrintln(e.getMessage)
            sys.exit(1)
          case Left(e) => throw e
          case Right(files) =>
            // Some progress lines seem to be scraped without this.
            Console.out.flush()

            val out =
              if (options.classpath)
                files
                  .map(_.toString)
                  .mkString(File.pathSeparator)
              else
                files
                  .map(_.toString)
                  .mkString("\n")

            println(out)
        }
    }

}
