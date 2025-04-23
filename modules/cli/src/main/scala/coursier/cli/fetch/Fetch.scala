package coursier.cli.fetch

import java.io.{File, PrintStream}
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.util.concurrent.ExecutorService

import caseapp._
import cats.data.Validated
import coursier.cli.{CoursierCommand, CommandGroup}
import coursier.cli.resolve.{Output, Resolve, ResolveException}
import coursier.core.Resolution
import coursier.install.Channels
import coursier.util.{Artifact, Sync, Task}

import scala.concurrent.ExecutionContext

object Fetch extends CoursierCommand[FetchOptions] {

  def task(
    params: FetchParams,
    pool: ExecutorService,
    args: Seq[String],
    stdout: PrintStream = System.out,
    stderr: PrintStream = System.err
  ): Task[(Resolution, Option[String], Option[String], Seq[(Artifact, File)])] = {

    val resolveTask = coursier.cli.resolve.Resolve.task(
      params.resolve,
      pool,
      System.out,
      System.err,
      args,
      force = params.artifact.force
    )

    val logger = params.resolve.output.logger()

    val cache = params.resolve.cache.cache(pool, logger)

    for {
      t <- resolveTask
      (res, scalaVersionOpt, platformOpt, _) = t
      artifacts = coursier.Artifacts.artifacts0(
        res,
        params.artifact.classifiers,
        params.artifact.attributes,
        Some(params.artifact.mainArtifacts), // allow to be null?
        Some(params.artifact.artifactTypes), // allow to be null?
        params.resolve.classpathOrder.getOrElse(true)
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
              val byArtifacts = artifacts.groupMap(_._3) { case (dep, pub, _) => (dep, pub) }
              val report = JsonReport.report(
                res,
                for {
                  (art, fileOpt) <- artifactFiles
                  depPubs        <- byArtifacts.get(art).toSeq
                  (dep, pub)     <- depPubs
                } yield (dep, pub, art, fileOpt)
              )

              Files.write(output, report.getBytes(StandardCharsets.UTF_8))
            }
          case None =>
            Task.point(())
        }
      }
    } yield (
      res,
      scalaVersionOpt,
      platformOpt,
      artifactFiles.collect { case (a, Some(f)) => a -> f }
    )
  }

  override def group: String = CommandGroup.resolve

  def run(options: FetchOptions, args: RemainingArgs): Unit = {

    var pool: ExecutorService = null

    // get options and dependencies from apps if any
    val (options0, deps) =
      FetchParams(options).toEither.toOption.fold((options, args.all)) { initialParams =>
        val initialRepositories = initialParams.resolve.repositories.repositories
        val channels            = initialParams.channel.channels
        pool = Sync.fixedThreadPool(initialParams.resolve.cache.parallel)
        val cache = initialParams.resolve.cache.cache(pool, initialParams.resolve.output.logger())
        val channels0 = Channels(channels, initialRepositories, cache)
        val res       = Resolve.handleApps(options, args.all, channels0)(_.addApp(_))
        res
      }

    val params = FetchParams(options0) match {
      case Validated.Invalid(errors) =>
        for (err <- errors.toList)
          Output.errPrintln(err)
        sys.exit(1)
      case Validated.Valid(p) => p
    }

    if (pool == null)
      pool = Sync.fixedThreadPool(params.resolve.cache.parallel)
    val ec = ExecutionContext.fromExecutorService(pool)

    val t = task(params, pool, deps)

    val (_, _, _, files) = t.attempt.unsafeRun(wrapExceptions = true)(ec) match {
      case Left(e: ResolveException) if params.resolve.output.verbosity <= 1 =>
        Output.errPrintln(e.message)
        sys.exit(1)
      case Left(e: coursier.error.FetchError) if params.resolve.output.verbosity <= 1 =>
        Output.errPrintln(e.getMessage)
        sys.exit(1)
      case Left(e)  => throw e
      case Right(t) => t
    }

    // Some progress lines seem to be scraped without this.
    Console.out.flush()

    val out =
      if (files.isEmpty) ""
      else if (options.classpath)
        files
          .map(_._2.toString)
          .mkString(File.pathSeparator) + System.lineSeparator()
      else
        files
          .map(_._2.toString + System.lineSeparator())
          .mkString

    print(out)
  }

}
