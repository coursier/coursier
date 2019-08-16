package coursier.cli.fetch

import java.io.{File, PrintStream}
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.util.concurrent.ExecutorService

import caseapp._
import cats.data.Validated
import coursier.cli.resolve.{Output, Resolve, ResolveException}
import coursier.core.Resolution
import coursier.util.{Artifact, Sync, Task}

import scala.concurrent.ExecutionContext

object Fetch extends CaseApp[FetchOptions] {

  def task(
    params: FetchParams,
    pool: ExecutorService,
    args: Seq[String],
    stdout: PrintStream = System.out,
    stderr: PrintStream = System.err
  ): Task[(Resolution, String, Option[String], Seq[(Artifact, File)])] = {

    val resolveTask = coursier.cli.resolve.Resolve.task(
      params.resolve,
      pool,
      System.out,
      System.err,
      args,
      printOutput = false,
      force = params.artifact.force
    )

    val logger = params.resolve.output.logger()

    val cache = params.resolve.cache.cache[Task](pool, logger)

    for {
      t <- resolveTask
      (res, scalaVersion, platformOpt) = t
      artifacts = coursier.Artifacts.artifacts0(
        res,
        params.artifact.classifiers,
        Some(params.artifact.mainArtifacts), // allow to be null?
        Some(params.artifact.artifactTypes),  // allow to be null?
        params.resolve.classpathOrder
      )

      artifactFiles <- coursier.Artifacts.fetchArtifacts(
        artifacts.map(_._3).distinct,
        cache
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
                printExclusions = false
              )

              Files.write(output, report.getBytes(StandardCharsets.UTF_8))
            }
          case None =>
            Task.point(())
        }
      }
    } yield (res, scalaVersion, platformOpt, artifactFiles)
  }

  def run(options: FetchOptions, args: RemainingArgs): Unit = {

    var pool: ExecutorService = null

    // get options and dependencies from apps if any
    val (options0, deps) = FetchParams(options).toEither.toOption.fold((options, args.all)) { initialParams =>
      val initialRepositories = initialParams.resolve.repositories.repositories
      val channels = initialParams.resolve.repositories.channels
      pool = Sync.fixedThreadPool(initialParams.resolve.cache.parallel)
      val cache = initialParams.resolve.cache.cache(pool, initialParams.resolve.output.logger())
      val res = Resolve.handleApps(options, args.all, channels, initialRepositories, cache)(_.addApp(_))
      res
    }

    FetchParams(options0) match {
      case Validated.Invalid(errors) =>
        for (err <- errors.toList)
          Output.errPrintln(err)
        sys.exit(1)
      case Validated.Valid(params) =>

        if (pool == null)
          pool = Sync.fixedThreadPool(params.resolve.cache.parallel)
        val ec = ExecutionContext.fromExecutorService(pool)

        val t = task(params, pool, deps)

        t.attempt.unsafeRun()(ec) match {
          case Left(e: ResolveException) if params.resolve.output.verbosity <= 1 =>
            Output.errPrintln(e.message)
            sys.exit(1)
          case Left(e: coursier.error.FetchError) if params.resolve.output.verbosity <= 1 =>
            Output.errPrintln(e.getMessage)
            sys.exit(1)
          case Left(e) => throw e
          case Right((_, _, _, files)) =>
            // Some progress lines seem to be scraped without this.
            Console.out.flush()

            val out =
              if (options.classpath)
                files
                  .map(_._2.toString)
                  .mkString(File.pathSeparator)
              else
                files
                  .map(_._2.toString)
                  .mkString("\n")

            println(out)
        }
    }
  }

}
