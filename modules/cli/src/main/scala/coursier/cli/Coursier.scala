package coursier.cli

import java.io.InputStream
import java.nio.charset.StandardCharsets
import java.util.Scanner

import caseapp.core.RemainingArgs
import caseapp.core.help.Help
import caseapp.core.parser.Parser
import coursier.cache.CacheUrl
import coursier.cli.bootstrap.Bootstrap
import coursier.cli.channel.Channel
import coursier.cli.complete.Complete
import coursier.cli.fetch.Fetch
import coursier.cli.get.Get
import coursier.cli.install.{Install, List, Uninstall, Update}
import coursier.cli.jvm.{Java, JavaHome}
import coursier.cli.launch.Launch
import coursier.cli.publish.Publish
import coursier.cli.resolve.Resolve
import coursier.cli.setup.{Setup, SetupOptions}
import coursier.cli.search.Search
import coursier.core.Version
import coursier.install.InstallDir
import coursier.launcher.internal.{FileUtil, Windows}
import shapeless._

import scala.util.control.NonFatal

object Coursier extends CommandAppPreA(Parser[LauncherOptions], Help[LauncherOptions], CoursierCommand.parser, CoursierCommand.help) {

  val isGraalvmNativeImage = sys.props.contains("org.graalvm.nativeimage.imagecode")

  if (System.console() != null && Windows.isWindows) {
    val useJni = coursier.paths.Util.useJni()
    try {
      if (useJni)
        coursier.jniutils.WindowsAnsiTerminal.enableAnsiOutput()
      else
        io.github.alexarchambault.windowsansi.WindowsAnsi.setup()
    } catch {
      case NonFatal(e) =>
        val doThrow = java.lang.Boolean.getBoolean("coursier.windows-ansi.throw-exception")
        if (doThrow || java.lang.Boolean.getBoolean("coursier.windows-ansi.verbose"))
          System.err.println(s"Error setting up Windows terminal for ANSI escape codes: $e")
        if (doThrow)
           throw e
    }
  }

  CacheUrl.setupProxyAuth()

  override val appName = "Coursier"
  override val progName =
    if (isGraalvmNativeImage) "cs"
    else "coursier"
  override val appVersion = coursier.util.Properties.version

  private def zshCompletions(): String = {
    var is: InputStream = null
    val b = try {
      is = Thread.currentThread()
        .getContextClassLoader
        .getResource("completions/zsh")
        .openStream()
      FileUtil.readFully(is)
    } finally {
      if (is != null)
        is.close()
    }
    new String(b, StandardCharsets.UTF_8)
  }

  private def runSetup(): Unit = {
    Setup.run(SetupOptions(banner = Some(true)), RemainingArgs(Nil, Nil))

    // https://stackoverflow.com/questions/26184409/java-console-prompt-for-enter-input-before-moving-on/26184535#26184535
    println("Press \"ENTER\" to continue...")
    val scanner = new Scanner(System.in)
    scanner.nextLine()
  }

  private def isInstalledLauncher: Boolean =
    System.getenv(InstallDir.isInstalledLauncherEnvVar) == "true"

  override def main(args: Array[String]): Unit = {

    val csArgs =
      if (isGraalvmNativeImage) {
        // process -J* args ourselves

        val (jvmArgs, csArgs0) = args.partition(_.startsWith("-J"))

        for (jvmArg <- jvmArgs) {
          val arg = jvmArg.stripPrefix("-J")
          if (arg.startsWith("-D"))
            arg.stripPrefix("-D").split("=", 2) match {
              case Array(k) => System.setProperty(k, "")
              case Array(k, v) => System.setProperty(k, v)
            }
          else
            System.err.println(s"Warning: ignoring unhandled -J argument: $jvmArg")
        }

        csArgs0
      } else
        args

    if (csArgs.nonEmpty)
      super.main(csArgs)
    else if (Windows.isWindows && !isInstalledLauncher)
      runSetup()
    else
      helpAsked()
  }

  def beforeCommand(options: LauncherOptions, remainingArgs: Seq[String]): Unit = {

    if(options.version) {
      System.out.println(appVersion)
      sys.exit(0)
    }

    for (requiredVersion <- options.require.map(_.trim).filter(_.nonEmpty)) {
      val requiredVersion0 = Version(requiredVersion)
      val currentVersion = coursier.util.Properties.version
      val currentVersion0 = Version(currentVersion)
      if (currentVersion0.compare(requiredVersion0) < 0) {
        System.err.println(s"Required version $requiredVersion > $currentVersion")
        sys.exit(1)
      }
    }

    options.completions.foreach {
      case "zsh" =>
        System.out.print(zshCompletions())
        sys.exit(0)
      case other =>
        System.err.println(s"Unrecognized or unsupported shell: $other")
        sys.exit(1)
    }
  }

  object runWithOptions extends Poly1 {
    private def opt[T](run: (T, RemainingArgs) => Unit) = at[T](run.curried)
    implicit val atBootstrap = opt[bootstrap.BootstrapOptions]    (Bootstrap.run)
    implicit val atChannel   = opt[channel.ChannelOptions]        (Channel.run)
    implicit val atComplete  = opt[complete.CompleteOptions]      (Complete.run)
    implicit val atFetch     = opt[fetch.FetchOptions]            (Fetch.run)
    implicit val atGet       = opt[get.GetOptions]                (Get.run)
    implicit val atInstall   = opt[install.InstallOptions]        (Install.run)
    implicit val atJava      = opt[jvm.JavaOptions]               (Java.run)
    implicit val atJavaHome  = opt[jvm.JavaHomeOptions]           (JavaHome.run)
    implicit val atLaunch    = opt[launch.LaunchOptions]          (Launch.run)
    implicit val atList      = opt[install.ListOptions]           (List.run)
    implicit val atPublish   = opt[publish.options.PublishOptions](Publish.run)
    implicit val atResolve   = opt[resolve.ResolveOptions]        (Resolve.run)
    implicit val atSearch    = opt[search.SearchOptions]          (Search.run)
    implicit val atSetup     = opt[setup.SetupOptions]            (Setup.run)
    implicit val atUninstall = opt[install.UninstallOptions]      (Uninstall.run)
    implicit val atUpdate    = opt[install.UpdateOptions]         (Update.run)
  }

  def runA = _.fold(runWithOptions)

}
