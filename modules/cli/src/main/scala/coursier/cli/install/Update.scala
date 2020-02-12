package coursier.cli.install

import java.time.Instant

import caseapp.core.app.CaseApp
import caseapp.core.RemainingArgs
import coursier.install.{Channels, InstallDir, Updatable}
import coursier.util.{Sync, Task}

object Update extends CaseApp[UpdateOptions] {
  def run(options: UpdateOptions, args: RemainingArgs): Unit = {

    val params = UpdateParams(options).toEither match {
      case Left(errors) =>
        for (err <- errors.toList)
          System.err.println(err)
        sys.exit(1)
      case Right(p) => p
    }

    val names =
      if (args.all.isEmpty)
        Updatable.list(params.shared.dir)
      else
        args.all

    val now = Instant.now()

    val pool = Sync.fixedThreadPool(params.cache.parallel)
    val cache = params.cache.cache(pool, params.output.logger())

    val graalvmHome = { version: String =>
      params.sharedJava.javaHome(cache, params.output.verbosity)
        .get(s"graalvm:$version")
    }

    val installDir =
      InstallDir(params.shared.dir, cache)
        .withGraalvmParamsOpt(params.shared.graalvmParamsOpt)
        .withCoursierRepositories(params.shared.repositories)
        .withVerbosity(params.output.verbosity)
        .withNativeImageJavaHome(Some(graalvmHome))

    val tasks = names.map { name =>
      installDir.maybeUpdate(
        name,
        source => Channels(Seq(source.channel), params.selectedRepositories(source.repositories), cache)
          .find(source.id)
          .map(_.map { case (_, path, descBytes) => (path, descBytes) }),
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

    task.unsafeRun()(cache.ec)
  }
}
