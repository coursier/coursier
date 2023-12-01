package coursier.cli.fetch

import java.io.{File, PrintStream}
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.util.concurrent.ExecutorService

import caseapp._
import cats.data.Validated
import coursier.cli.{CoursierCommand, CommandGroup}
import coursier.cli.resolve.{Output, Resolve, ResolveException}
import coursier.core.{Dependency, Resolution, Publication, Project}
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
      artifacts = coursier.Artifacts.artifacts(
        res,
        params.artifact.classifiers,
        Some(params.artifact.mainArtifacts), // allow to be null?
        Some(params.artifact.artifactTypes), // allow to be null?
        params.resolve.classpathOrder.getOrElse(true)
      )
      distinctArtifacts = artifacts.map(_._3).distinct
      artifactFiles <- coursier.Artifacts.fetchArtifacts(
        distinctArtifacts,
        cache
        // FIXME Allow to adjust retryCount via CLI args?
      )

      parentArtifacts = {
        def parents(child: Project): Seq[(Dependency, Publication, Artifact)] =
          child.parent.flatMap {
            case mv @ (module, version) => res.projectCache.get(mv).map {
                case (artifactSource, project) =>
                  val dependency = Dependency(module, version)
                  val artifacts  = artifactSource.artifacts(dependency, project, None)
                  artifacts.map { case (pub, art) => (dependency, pub, art) } ++ parents(project)
              }
          }
            .toSeq.flatten
        res.projectCache.values.flatMap {
          case (_, proj) => parents(proj)
        }.toSeq.distinct
      }
      metadataFiles <- coursier.Artifacts.fetchArtifacts(
        (distinctArtifacts.flatMap(_.metadata) ++ parentArtifacts.flatMap(_._3.metadata)).distinct,
        cache
      )
      checksums <- if (params.jsonOutputOpt.isDefined)
        coursier.Artifacts.fetchChecksums(
          pool,
          Set("MD5", "SHA-1"),
          (distinctArtifacts ++ (distinctArtifacts ++ parentArtifacts.map(_._3)).map(
            _.metadata
          ).flatten).distinct,
          cache
        ).map(Some(_))
      else Task.point(None)

      _ <- {
        params.jsonOutputOpt match {
          case Some(output) =>
            Task.delay {
              val report = JsonOutput.report(
                res,
                artifacts ++ parentArtifacts,
                (artifactFiles ++ metadataFiles).collect { case (a, Some(f)) => a -> f },
                params.artifact.classifiers,
                checksums,
                printExclusions = false
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

    val (_, _, _, files) = t.attempt.unsafeRun()(ec) match {
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
