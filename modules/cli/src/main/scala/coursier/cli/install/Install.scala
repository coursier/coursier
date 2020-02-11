package coursier.cli.install

import java.io.File
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.time.Instant

import caseapp.core.app.CaseApp
import caseapp.core.RemainingArgs
import coursier.cli.Util.ValidatedExitOnError
import coursier.install.{Channels, InstallDir, RawSource}
import coursier.launcher.internal.Windows
import coursier.util.Sync

object Install extends CaseApp[InstallOptions] {

  def run(options: InstallOptions, args: RemainingArgs): Unit = {

    val params = InstallParams(options).exitOnError()

    if (params.output.verbosity >= 1)
      System.err.println(s"Using install directory ${params.shared.dir}")

    if (Files.exists(params.shared.dir)) {
      if (params.output.verbosity >= 0 && !Files.isDirectory(params.shared.dir))
        System.err.println(s"Warning: ${params.shared.dir} doesn't seem to be a directory")
    } else
      Files.createDirectories(params.shared.dir)

    val pool = Sync.fixedThreadPool(params.cache.parallel)
    val cache = params.cache.cache(pool, params.output.logger())

    if (args.all.nonEmpty && params.channels.isEmpty) {
      System.err.println(s"Error: no channels specified")
      sys.exit(1)
    }

    if (params.installChannels.nonEmpty) {

      // TODO Move to install module

      val configDir = coursier.paths.CoursierPaths.configDirectory()
      val channelDir = new File(configDir, "channels")

      // FIXME May not be fine with concurrency (two process doing this in parallel)
      val f = Stream.from(1)
        .map { n =>
          new File(channelDir, s"channels-$n")
        }
        .filter(!_.exists())
        .head

      if (params.output.verbosity >= 1)
        System.err.println(s"Writing $f")
      Files.createDirectories(f.toPath.getParent)
      Files.write(f.toPath, params.installChannels.map(_ + "\n").mkString.getBytes(StandardCharsets.UTF_8))
    }

    if (args.all.isEmpty) {
      if (params.output.verbosity >= 0 && params.installChannels.isEmpty)
        System.err.println("Nothing to install")
      sys.exit(0)
    }

    val installDir = InstallDir(params.shared.dir, cache)
      .withVerbosity(params.output.verbosity)
      .withGraalvmParamsOpt(params.shared.graalvmParamsOpt)
      .withCoursierRepositories(params.shared.repositories)

    val channels = Channels(params.channels, params.shared.repositories, cache)
      .withVerbosity(params.output.verbosity)

    for (id <- args.all) {

      val appInfo = channels.appDescriptor(id).attempt.unsafeRun()(cache.ec) match {
        case Left(err: Channels.ChannelsException) =>
          System.err.println(err.getMessage)
          sys.exit(1)
        case Left(err) => throw err
        case Right(appInfo) => appInfo
      }

      val wroteSomething = installDir.createOrUpdate(
        appInfo,
        Instant.now(),
        force = params.force
      )

      if (wroteSomething)
        System.err.println(s"Wrote ${appInfo.source.id}")
      else if (params.output.verbosity >= 1)
        System.err.println(s"${appInfo.source.id} doesn't need updating")
    }

    if (params.output.verbosity >= 0) {
      val path = Option(System.getenv("PATH"))
        .toSeq
        .flatMap(_.split(File.pathSeparatorChar).toSeq)
        .toSet

      if (!path(params.shared.dir.toAbsolutePath.toString)) {
        System.err.println(s"Warning: ${params.shared.dir} is not in your PATH")
        if (!Windows.isWindows)
          System.err.println(
            s"""To fix that, add the following line to ${ShellUtil.rcFileOpt.getOrElse("your shell configuration file")}
               |
               |export PATH="$$PATH:${params.shared.dir.toAbsolutePath}"""".stripMargin
          )
      }
    }
  }

}
