package coursier.cli.install

import java.io.File
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.time.Instant

import caseapp.core.RemainingArgs
import coursier.cli.channel.Channel
import coursier.cli.{CoursierCommand, CommandGroup}
import coursier.cli.setup.MaybeSetupPath
import coursier.cli.Util.ValidatedExitOnError
import coursier.install.{Channels, InstallDir, RawSource}
import coursier.install.error.InstallDirException
import coursier.launcher.internal.Windows
import coursier.paths.Util
import coursier.util.Sync

import scala.concurrent.duration.Duration
import scala.util.Properties

object Install extends CoursierCommand[InstallOptions] {

  override def group: String = CommandGroup.install

  def run(options: InstallOptions, args: RemainingArgs): Unit = {

    val params = InstallParams(options, args.all.nonEmpty).exitOnError()

    if (params.output.verbosity >= 1)
      System.err.println(s"Using install directory ${params.shared.dir}")

    if (Files.exists(params.shared.dir)) {
      if (params.output.verbosity >= 0 && !Files.isDirectory(params.shared.dir))
        System.err.println(s"Warning: ${params.shared.dir} doesn't seem to be a directory")
    }
    else
      Util.createDirectories(params.shared.dir)

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
      ).get(graalVmVersion)
    }

    val installDir = params.shared.installDir(cache, params.repository.repositories)
      .withVerbosity(params.output.verbosity)
      .withNativeImageJavaHome(Some(graalvmHome))

    if (params.installChannels.nonEmpty) {
      val progName = coursier.cli.Coursier.progName
      val options  = params.installChannels.flatMap(c => Seq("--add", c)).mkString(" ")
      System.err.println(
        s"Warning: the --add-channel option is deprecated. Use '$progName channel $options' instead."
      )

      Channel.addChannel(params.installChannels.toList, params.output)
    }
    else if (params.env.env) {
      val script =
        if (params.env.windowsScript)
          installDir.envUpdate.batScript
        else
          installDir.envUpdate.bashScript
      println(script)
    }
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
        if (params.env.windowsScript)
          s"""set "PATH=$s"""" + "\r\n"
        else
          s"""export PATH="$s"""" + "\n"
      }
      print(script)
    }
    else if (params.env.setup) {
      val task = params.env.setupTask(
        installDir.envUpdate,
        params.env.envVarUpdater,
        params.output.verbosity,
        MaybeSetupPath.headerComment
      )
      task.unsafeRun()(cache.ec)
    }
    else {

      if (args.all.isEmpty) {
        if (params.output.verbosity >= 0 && params.installChannels.isEmpty)
          System.err.println("Nothing to install")
        sys.exit(0)
      }

      val channels = Channels(params.channels, params.repository.repositories, cache)
        .withVerbosity(params.output.verbosity)

      try for (id <- args.all) {

          val appInfo = channels.appDescriptor(id).attempt.unsafeRun()(cache.ec) match {
            case Left(err: Channels.ChannelsException) =>
              System.err.println(err.getMessage)
              sys.exit(1)
            case Left(err)      => throw err
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
                System.err.println(
                  s"Could not install ${appInfo.source.id} (concurrent operation ongoing)"
                )
          }
        }
      catch {
        case e: InstallDirException =>
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
          if (!Properties.isWin) {
            val rcFile = ShellUtil.rcFileOpt.getOrElse("your shell configuration file")
            System.err.println(
              s"""To fix that, add the following line to $rcFile
                 |
                 |export PATH="$$PATH:${params.shared.dir.toAbsolutePath}"""".stripMargin
            )
          }
        }
      }
    }
  }

}
