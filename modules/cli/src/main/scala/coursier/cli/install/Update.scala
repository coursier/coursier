package coursier.cli.install

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path}
import java.time.Instant

import caseapp.core.RemainingArgs
import caseapp.core.app.CaseApp
import coursier.cli.app.RawAppDescriptor
import coursier.cli.util.Guard
import coursier.util.Sync

import scala.collection.JavaConverters._

object Update extends CaseApp[UpdateOptions] {
  def run(options: UpdateOptions, args: RemainingArgs): Unit = {

    Guard()

    val params = UpdateParams(options).toEither match {
      case Left(errors) =>
        for (err <- errors.toList)
          System.err.println(err)
        sys.exit(1)
      case Right(p) => p
    }

    val launchers =
      if (args.all.isEmpty) {
        var s: java.util.stream.Stream[Path] = null
        s = Files.list(params.dir)
        try {
          s.iterator()
            .asScala
            .filter { p =>
              val name = p.getFileName.toString
              !name.startsWith(".") &&
                !name.endsWith(".bat") &&
                Files.isRegularFile(p)
            }
            .toVector
            .sortBy(_.getFileName.toString)
        } finally {
          if (s != null)
            s.close()
        }
      } else
        args.all.map(params.dir.resolve)

    val now = Instant.now()

    val pool = Sync.fixedThreadPool(params.shared.cache.parallel)
    val cache = params.shared.cache.cache(pool, params.shared.logger())

    for (launcher <- launchers) {
      if (params.shared.verbosity >= 2)
        System.err.println(s"Looking at ${params.dir.relativize(launcher)}")

      val infoFile = {
        val f = launcher.getParent.resolve(s".${launcher.getFileName}.info")
        if (Files.isRegularFile(f))
          f
        else
          launcher
      }

      val updatedDescOpt =
        for {
          (s, _) <- AppGenerator.readSource(infoFile)
          repositories = if (params.overrideRepositories) params.repositories else s.repositories
          (_, path, a) <- Channels.find(Seq(s.channel), s.id, cache, repositories)
        } yield {
          val e = RawAppDescriptor.parse(new String(a, StandardCharsets.UTF_8))
            .left.map(err => new AppGenerator.ErrorParsingAppDescription(path, err))
            .right.flatMap { r =>
              r.appDescriptor
                .toEither
                .left.map { errors =>
                  new AppGenerator.ErrorProcessingAppDescription(path, errors.toList.mkString(", "))
                }
            }
          val desc = e.fold(throw _, identity)
          (desc, a)
        }

      val written = AppGenerator.createOrUpdate(
        updatedDescOpt,
        None,
        cache,
        params.dir,
        launcher,
        now,
        params.shared.verbosity,
        params.shared.forceUpdate,
        params.shared.graalvmParamsOpt,
        coursierRepositories = params.repositories
      )
      if (!written && params.shared.verbosity >= 1)
        System.err.println(s"No new update for ${params.dir.relativize(launcher)}\n")
    }
  }
}
