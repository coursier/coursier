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

    val installDir =
      InstallDir(params.shared.dir, cache)
        .withGraalvmParamsOpt(params.shared.graalvmParamsOpt)
        .withCoursierRepositories(params.shared.repositories)
        .withVerbosity(params.output.verbosity)

    val tasks = names.map { name =>
      installDir.maybeUpdate(
        name,
        source => Channels(Seq(source.channel), params.selectedRepositories(source.repositories), cache)
          .find(source.id)
          .map(_.map { case (_, path, descBytes) => (path, descBytes) }),
        now,
        params.force
      ).map {
        case true =>
          if (params.output.verbosity >= 0)
            System.err.println(s"Updated $name")
        case false =>
      }
    }

    val task = tasks.foldLeft(Task.point(())) { (acc, t) =>
      for (_ <- acc; _ <- t) yield ()
    }

    task.unsafeRun()(cache.ec)
  }
}
