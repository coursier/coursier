package coursier.cli.install

import java.time.Instant

import caseapp.core.RemainingArgs
import coursier.cli.{CoursierCommand, CommandGroup}
import coursier.install.{Channels, InstallDir}
import coursier.install.error.InstallDirException
import coursier.util.{Sync, Task}

import scala.concurrent.duration.Duration

object Update extends CoursierCommand[UpdateOptions] {

  override def group: String = CommandGroup.install

  def run(options: UpdateOptions, args: RemainingArgs): Unit = {

    val params = UpdateParams(options).toEither match {
      case Left(errors) =>
        for (err <- errors.toList)
          System.err.println(err)
        sys.exit(1)
      case Right(p) => p
    }

    val now = Instant.now()

    val pool  = Sync.fixedThreadPool(params.cache.parallel)
    val cache = params.cache.cache(pool, params.output.logger())
    val noUpdateCoursierCache =
      params.cache.cache(pool, params.output.logger(), overrideTtl = Some(Duration.Inf))

    val graalvmHome = { graalVmVersion: String =>
      params.sharedJava.javaHome(
        cache,
        noUpdateCoursierCache,
        params.repository.repositories,
        params.output.verbosity
      )
        .get(graalVmVersion)
    }

    val installDir = params.shared.installDir(cache, params.repository.repositories)
      .withVerbosity(params.output.verbosity)
      .withNativeImageJavaHome(Some(graalvmHome))

    val names =
      if (args.all.isEmpty)
        installDir.list()
      else
        args.all

    val tasks = names.map { name =>
      installDir.maybeUpdate(
        name,
        source =>
          Channels(Seq(source.channel), params.selectedRepositories(source.repositories), cache)
            .find(source.id)
            .map(_.map(data => (data.origin, data.data))),
        now,
        params.force
      ).map {
        case None =>
          if (params.output.verbosity >= 0)
            System.err.println(s"Could not update $name (concurrent operation ongoing)")
        case Some(true) =>
          if (params.output.verbosity >= 0)
            System.err.println(s"Updated $name")
        case Some(false) =>
      }
    }

    val task = tasks.foldLeft(Task.point(())) { (acc, t) =>
      for (_ <- acc; _ <- t) yield ()
    }

    try task.unsafeRun()(cache.ec)
    catch {
      case e: InstallDirException =>
        System.err.println(e.getMessage)
        if (params.output.verbosity >= 2)
          throw e
        else
          sys.exit(1)
    }
  }
}
