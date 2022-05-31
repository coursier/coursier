package coursier.cli

import caseapp.core.app.CommandsEntryPoint
import caseapp.RemainingArgs
import coursier.cache.CacheUrl
import coursier.cli.internal.{Argv0, PathUtil}
import coursier.cli.setup.{Setup, SetupOptions}
import coursier.install.InstallDir
import coursier.jniutils.ModuleFileName
import coursier.proxy.SetupProxy

import java.nio.file.Paths
import java.util.Scanner

import scala.util.control.NonFatal
import scala.util.Properties
import caseapp.core.help.HelpFormat

object Coursier extends CommandsEntryPoint {

  private def isGraalvmNativeImage: Boolean =
    sys.props.contains("org.graalvm.nativeimage.imagecode")

  lazy val progName = (new Argv0).get("coursier")

  override val description =
    """|Coursier is the Scala application and artifact manager.
       |It can install Scala applications and setup your Scala development environment.
       |It can also download and cache artifacts from the web.""".stripMargin

  val commands = Seq(
    bootstrap.Bootstrap,
    channel.Channel,
    coursier.cli.complete.Complete,
    fetch.Fetch,
    get.Get,
    install.Install,
    jvm.Java,
    jvm.JavaHome,
    launch.Launch,
    install.List,
    publish.Publish,
    resolve.Resolve,
    search.Search,
    setup.Setup,
    version.Version,
    install.Uninstall,
    install.Update
  )

  override def enableCompleteCommand    = true
  override def enableCompletionsCommand = true

  private def isNonInstalledLauncherWindows: Boolean =
    Properties.isWin && isGraalvmNativeImage && {
      val p = Paths.get(ModuleFileName.get())
      !PathUtil.isInPath(p)
    }

  private def runSetup(): Unit = {
    Setup.run(SetupOptions(banner = Some(true)), RemainingArgs(Nil, Nil))

    // https://stackoverflow.com/questions/26184409/java-console-prompt-for-enter-input-before-moving-on/26184535#26184535
    println("Press \"ENTER\" to continue...")
    val scanner = new Scanner(System.in)
    scanner.nextLine()
  }

  override def main(args: Array[String]): Unit = {

    if (Properties.isWin && isGraalvmNativeImage)
      // The DLL loaded by LoadWindowsLibrary is statically linked in
      // the coursier native image, no need to manually load it.
      coursier.jniutils.LoadWindowsLibrary.assumeInitialized()

    if (System.console() != null && Properties.isWin) {
      val useJni = coursier.paths.Util.useJni()
      try if (useJni)
        coursier.jniutils.WindowsAnsiTerminal.enableAnsiOutput()
      else
        io.github.alexarchambault.windowsansi.WindowsAnsi.setup()
      catch {
        case NonFatal(e) =>
          val doThrow = java.lang.Boolean.getBoolean("coursier.windows-ansi.throw-exception")
          if (doThrow || java.lang.Boolean.getBoolean("coursier.windows-ansi.verbose"))
            System.err.println(s"Error setting up Windows terminal for ANSI escape codes: $e")
          if (doThrow)
            throw e
      }
    }

    val csArgs =
      if (isGraalvmNativeImage) {
        // process -J* args ourselves

        val (jvmArgs, csArgs0) = args.partition(_.startsWith("-J"))

        for (jvmArg <- jvmArgs) {
          val arg = jvmArg.stripPrefix("-J")
          if (arg.startsWith("-D"))
            arg.stripPrefix("-D").split("=", 2) match {
              case Array(k)    => System.setProperty(k, "")
              case Array(k, v) => System.setProperty(k, v)
            }
          else
            System.err.println(s"Warning: ignoring unhandled -J argument: $jvmArg")
        }

        csArgs0
      }
      else
        args

    SetupProxy.setup()

    if (csArgs.nonEmpty)
      super.main(csArgs)
    else if (isNonInstalledLauncherWindows)
      runSetup()
    else {
      println(help.help(helpFormat, showHidden = false))
      sys.exit(1)
    }
  }

  override def helpFormat: HelpFormat =
    HelpFormat.default()
      .withSortedCommandGroups(Some(CommandGroup.order))
}
