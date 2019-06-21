package coursier.cli.publish

import java.io.{File, PrintStream}
import java.nio.file.{Files, Paths}
import java.time.Instant
import java.util.concurrent.ExecutorService

import coursier.{Repositories, dependencyString}
import coursier.cache.{Cache, CacheLogger}
import coursier.cli.publish.params.PublishParams
import coursier.cli.publish.util.DeleteOnExit
import coursier.publish.dir.Dir
import coursier.publish.dir.logger.{BatchDirLogger, InteractiveDirLogger}
import coursier.publish.fileset.FileSet
import coursier.publish.sbt.Sbt
import coursier.util.Task

import scala.concurrent.duration.Duration
import scala.concurrent.{Await, ExecutionContext}

object Input {

  def manualPackageFileSetOpt(params: PublishParams, now: Instant): Task[Option[FileSet]] =
    if (params.singlePackage.`package`)
      Task.fromEither(
        Manual.manualPackageFileSet(params.singlePackage, params.metadata, now)
          .right.map(Some(_))
      )
    else
      Task.point(None)

  def dirFileSet(
    params: PublishParams,
    out: PrintStream
  ): Task[FileSet] =
    params
      .directory
      .directories
      .map { d =>
        val logger =
          if (params.batch)
            new BatchDirLogger(out, params.dirName(d), params.verbosity)
          else
            InteractiveDirLogger.create(out, params.dirName(d), params.verbosity)
        Dir.read(d, logger)
      }
      // the logger will have to be shared if this is to be parallelized
      .foldLeft(Task.point(FileSet.empty)) { (acc, t) =>
        for {
          a <- acc
          extra <- t
        } yield a ++ extra
      }

  def sbtCsPublishJarTask(cache: Cache[Task]): Task[File] =
    Task.delay {
      val files = coursier.Fetch(cache)
        .addRepositories(Repositories.sbtPlugin("releases"))
        .addDependencies(
          dep"io.get-coursier:sbt-cs-publish;scalaVersion=2.12;sbtVersion=1.0:0.1.1"
        )
        .run()

      files match {
        case Seq() => ???
        case Seq(jar) => jar
        case other => ???
      }
    }

  def sbtFileSet(
    params: PublishParams,
    now: Instant,
    out: PrintStream,
    deleteOnExit: DeleteOnExit,
    maybeReadCurrentDir: Boolean,
    pool: ExecutorService
  ): Task[FileSet] = {

    val actualSbtDirectoriesTask =
      if (maybeReadCurrentDir && params.directory.directories.isEmpty && params.directory.sbtDirectories.isEmpty) {
        val cwd = Paths.get(".")
        Task.delay(Sbt.isSbtProject(cwd)).map {
          case true =>
            Seq(cwd)
          case false =>
            Nil
        }
      } else
        Task.point(params.directory.sbtDirectories)

    actualSbtDirectoriesTask.flatMap { actualSbtDirectories =>
      actualSbtDirectories
        .map { sbtDir =>
          for {
            sbtStructureJar <- sbtCsPublishJarTask(params.cache.cache(pool, CacheLogger.nop))
            t <- Task.delay {
              val sbt = new Sbt(
                sbtDir.toFile,
                sbtStructureJar,
                ExecutionContext.global,
                params.sbtOutputFrame,
                params.verbosity,
                interactive = !params.batch
              )
              val tmpDir = Files.createTempDirectory("coursier-publish-sbt-")
              deleteOnExit(tmpDir)
              val f = sbt.publishTo(tmpDir.toFile)
              // meh, blocking from a task…
              Await.result(f, Duration.Inf)
              val dirLogger =
                if (params.batch)
                  new BatchDirLogger(out, params.dirName(tmpDir, Some("temporary directory")), params.verbosity)
                else
                  InteractiveDirLogger.create(out, params.dirName(tmpDir, Some("temporary directory")), params.verbosity)
              Dir.read(tmpDir, dirLogger)
            }
            fs <- t
          } yield fs
        }
        // DirLogger will have to be shared to parallelize this
        .foldLeft(Task.point(FileSet.empty)) { (acc, t) =>
        for {
          a <- acc
          extra <- t
        } yield a ++ extra
      }
    }
  }

}
