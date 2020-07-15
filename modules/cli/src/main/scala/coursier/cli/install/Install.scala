package coursier.cli.install

import java.io.File
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.time.Instant

import caseapp.core.app.CaseApp
import caseapp.core.RemainingArgs
import coursier.cli.setup.MaybeSetupPath
import coursier.cli.Util.ValidatedExitOnError
import coursier.install.{Channels, InstallDir, RawSource}
import coursier.launcher.internal.Windows
import coursier.paths.Util
import coursier.util.Sync

import scala.concurrent.duration.Duration

object Install extends CaseApp[InstallOptions] {

  def run(options: InstallOptions, args: RemainingArgs): Unit = {

    val params = InstallParams(options, args.all.nonEmpty).exitOnError()

    if (params.output.verbosity >= 1)
      System.err.println(s"Using install directory ${params.shared.dir}")

    if (Files.exists(params.shared.dir)) {
      if (params.output.verbosity >= 0 && !Files.isDirectory(params.shared.dir))
        System.err.println(s"Warning: ${params.shared.dir} doesn't seem to be a directory")
    } else
      Util.createDirectories(params.shared.dir)

    val pool = Sync.fixedThreadPool(params.cache.parallel)
    val cache = params.cache.cache(pool, params.output.logger())
    val noUpdateCoursierCache = params.cache.cache(pool, params.output.logger(), overrideTtl = Some(Duration.Inf))

    val graalvmHome = { version: String =>
      params.sharedJava.javaHome(cache, noUpdateCoursierCache, params.output.verbosity)
        .get(s"graalvm:$version")
    }

    val installDir = params.shared.installDir(cache)
      .withVerbosity(params.output.verbosity)
      .withNativeImageJavaHome(Some(graalvmHome))

    if (params.installChannels.nonEmpty) {

      // TODO Move to install module

      val configDir = coursier.paths.CoursierPaths.defaultConfigDirectory()
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
      Util.createDirectories(f.toPath.getParent)
      Files.write(f.toPath, params.installChannels.map(_ + "\n").mkString.getBytes(StandardCharsets.UTF_8))
    } else if (params.env.env)
      println(installDir.envUpdate.script)
    else if (params.env.disableEnv) {
      // TODO Move that to InstallDir?
      val dir = installDir.baseDir.toAbsolutePath.toString
      val updatedPath = Option(System.getenv("PATH")).flatMap { strPath =>
        val path = strPath.split(File.pathSeparator)
        if (path.contains(dir))
          Some(path.filter(_ != dir).mkString(File.pathSeparator)) // FIXME Only remove first one?
        else
          None
      }
      val script = updatedPath.fold("") { s =>
        // FIXME Escaping in s
        s"""export PATH="$s"""" + "\n"
      }
      print(script)
    } else if (params.env.setup) {
      val task = params.env.setupTask(
        installDir.envUpdate,
        params.env.envVarUpdater,
        params.output.verbosity,
        MaybeSetupPath.headerComment
      )
      task.unsafeRun()(cache.ec)
    } else {

      if (args.all.isEmpty) {
        if (params.output.verbosity >= 0 && params.installChannels.isEmpty)
          System.err.println("Nothing to install")
        sys.exit(0)
      }

      val channels = Channels(params.channels, params.shared.repositories, cache)
        .withVerbosity(params.output.verbosity)

      try {
        for (id <- args.all) {

          val appInfo = channels.appDescriptor(id).attempt.unsafeRun()(cache.ec) match {
            case Left(err: Channels.ChannelsException) =>
              System.err.println(err.getMessage)
              sys.exit(1)
            case Left(err) => throw err
            case Right(appInfo) => appInfo
          }

          val wroteSomethingOpt = installDir.createOrUpdate(
            appInfo,
            Instant.now(),
            force = params.force
          )

          wroteSomethingOpt match {
            case Some(true) =>
              if (params.output.verbosity >= 0)
                System.err.println(s"Wrote ${appInfo.source.id}")
            case Some(false) =>
              if (params.output.verbosity >= 1)
                System.err.println(s"${appInfo.source.id} doesn't need updating")
            case None =>
              if (params.output.verbosity >= 0)
                System.err.println(s"Could not install ${appInfo.source.id} (concurrent operation ongoing)")
          }
        }
      } catch {
        case e: InstallDir.InstallDirException =>
          System.err.println(e.getMessage)
          if (params.output.verbosity >= 2)
            throw e
          else
            sys.exit(1)
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

}
