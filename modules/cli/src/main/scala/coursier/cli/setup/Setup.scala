package coursier.cli.setup

import caseapp.core.app.CaseApp
import caseapp.core.RemainingArgs
import coursier.cache.Cache
import coursier.cli.Util.ValidatedExitOnError
import coursier.env.{EnvironmentUpdate, ProfileUpdater, WindowsEnvVarUpdater}
import coursier.install.{Channels, InstallDir}
import coursier.jvm.{JvmCacheLogger, JavaHome}
import coursier.launcher.internal.Windows
import coursier.util.{Sync, Task}

object Setup extends CaseApp[SetupOptions] {

  private def confirm(message: String): Task[Boolean] =
    Task.delay {
      // TODO
      System.out.println(message + " [Y/n]\nY")
      true
    }

  def maybeInstallJvm(
    coursierCache: Cache[Task],
    envVarUpdater: Either[WindowsEnvVarUpdater, ProfileUpdater],
    jvmCacheLogger: JvmCacheLogger
  ): Task[Unit] =
    for {
      baseHandle <- JavaHome.default
      handle = baseHandle
        .withJvmCacheLogger(jvmCacheLogger)
        .withCoursierCache(coursierCache)

      javaHomeOpt <- handle.system()

      idJavaHomeOpt <- javaHomeOpt match {
        case Some(javaHome) =>
          System.out.println(s"Found a JVM installed under $javaHome.") // Task.delay(…)
          Task.point(Some(JavaHome.systemId -> javaHome))
        case None =>
          confirm("No JVM found, should we try to install one?").flatMap {
            case false =>
              Task.point(None)
            case true =>
              System.out.println("No JVM found, trying to install one.") // Task.delay(…)
              baseHandle.getWithRetainedId(JavaHome.defaultJvm).map(Some(_))
          }
      }

      envUpdate = idJavaHomeOpt match {
        case Some((id, javaHome)) =>
          handle.environmentFor(id, javaHome)
        case None =>
          EnvironmentUpdate.empty
      }

      updatedSomething <- {

        envVarUpdater match {
          case Left(windowsEnvVarUpdater) =>
            if (envUpdate.isEmpty) Task.point(false)
            else {
              confirm(s"Should we update the ${(envUpdate.set.map(_._1) ++ envUpdate.pathLikeAppends.map(_._1)).mkString(", ")} environment variable(s)?")
                .flatMap {
                  case false => Task.point(false)
                  case true =>
                    Task.delay {
                      windowsEnvVarUpdater.applyUpdate(envUpdate)
                    }
                }
            }
          case Right(profileUpdater) =>
            lazy val profileFiles = profileUpdater.profileFiles() // Task.delay(…)
            if (envUpdate.isEmpty || profileFiles.isEmpty /* just in case, should not happen */)
              Task.point(false)
            else {
              val profileFilesStr = profileFiles.map(_.toString.replaceAllLiterally(sys.props("user.home"), "~"))
              confirm(s"Should we update ${profileFilesStr.mkString(", ")}?").flatMap {
                case false => Task.point(false)
                case true =>
                  Task.delay {
                    profileUpdater.applyUpdate(envUpdate, "JVM installed by coursier")
                  }
              }
            }
        }
      }

      _ <- {
        if (updatedSomething)
          Task.delay {
            val messageStart =
              if (envVarUpdater.isLeft)
                "Some global environment variables were updated."
              else
                "Some shell configuration files were updated."

            val message =
              messageStart + " It is recommended to close this terminal once " +
                "the setup command is done, and open a new one " +
                "for the changes to be taken into account."

            println(message)
          }
        else
          Task.point(())
      }

    } yield ()

  def maybeSetupPath(
    installDir: InstallDir,
    envVarUpdater: Either[WindowsEnvVarUpdater, ProfileUpdater],
  ): Task[Unit] = {

    val binDir = installDir.baseDir
    val binDirStr = binDir.toAbsolutePath.toString.replaceAllLiterally(sys.props("user.home"), "~")

    val envUpdate = EnvironmentUpdate()
      .withPathLikeAppends(Seq("PATH" -> binDir.toAbsolutePath.toString))

    envVarUpdater match {
      case Left(windowsEnvVarUpdater) =>
        confirm(s"Should we add $binDirStr to your PATH?").flatMap {
          case false => Task.point(())
          case true =>
            Task.delay {
              windowsEnvVarUpdater.applyUpdate(envUpdate)
            }
        }
      case Right(profileUpdater) =>
        val profileFilesStr = profileUpdater.profileFiles().map(_.toString.replaceAllLiterally(sys.props("user.home"), "~"))
        confirm(s"Should we add $binDirStr to your PATH via ${profileFilesStr.mkString(", ")}?").flatMap {
          case false => Task.point(())
          case true =>
            Task.delay {
              profileUpdater.applyUpdate(envUpdate, "coursier install directory")
            }
        }
    }
  }

  def maybeInstallApps(
    installDir: InstallDir,
    channels: Channels,
    appIds: Seq[String]
  ): Task[Unit] = {

    val tasks = appIds
      .map { id =>
        for {
          appInfo <- channels.appDescriptor(id)
          _ <- Task.delay(installDir.createOrUpdate(appInfo))
        } yield ()
      }

    tasks.foldLeft(Task.point(()))((acc, t) => acc.flatMap(_ => t))
  }


  def defaultAppList: Seq[String] =
    Seq(
      "ammonite",
      "cs",
      "coursier",
      "scala",
      "scalac",
      "sbt-launcher",
      "scalafmt"
    )

  def run(options: SetupOptions, args: RemainingArgs): Unit = {

    val params = SetupParams(options).exitOnError()

    val pool = Sync.fixedThreadPool(params.cache.parallel)
    val logger = params.output.logger()
    val jvmCacheLogger = params.sharedJava.jvmCacheLogger(params.output.verbosity)
    val cache = params.cache.cache(pool, logger)

    val envVarUpdater =
      if (Windows.isWindows)
        Left(WindowsEnvVarUpdater())
      else
        Right(
          ProfileUpdater()
            .withHome(params.homeOpt.orElse(ProfileUpdater.defaultHome))
        )

    val installDir = InstallDir(params.sharedInstall.dir, cache)
      .withVerbosity(params.output.verbosity)
      .withGraalvmParamsOpt(params.sharedInstall.graalvmParamsOpt)
      .withCoursierRepositories(params.sharedInstall.repositories)

    val channels = Channels(params.sharedChannel.channels, params.sharedInstall.repositories, cache)
      .withVerbosity(params.output.verbosity)

    val tasks = Seq(
      maybeInstallJvm(cache, envVarUpdater, jvmCacheLogger),
      maybeSetupPath(installDir, envVarUpdater),
      maybeInstallApps(installDir, channels, defaultAppList)
    )

    val task = tasks.foldLeft(Task.point(()))((acc, t) => acc.flatMap(_ => t))

    logger.use {
      // TODO Better error messages for relevant exceptions
      task.unsafeRun()(cache.ec)
    }
  }
}
