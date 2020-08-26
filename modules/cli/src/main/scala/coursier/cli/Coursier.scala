package coursier.cli

import java.io.InputStream
import java.nio.charset.StandardCharsets
import java.util.Scanner

import caseapp.core.RemainingArgs
import caseapp.core.help.Help
import caseapp.core.parser.Parser
import coursier.cli.bootstrap.Bootstrap
import coursier.cli.complete.Complete
import coursier.cli.fetch.Fetch
import coursier.cli.get.Get
import coursier.cli.install.{Install, List, Uninstall, Update}
import coursier.cli.jvm.{Java, JavaHome}
import coursier.cli.launch.Launch
import coursier.cli.publish.Publish
import coursier.cli.resolve.Resolve
import coursier.cli.setup.{Setup, SetupOptions}
import coursier.core.Version
import coursier.install.InstallDir
import coursier.launcher.internal.{FileUtil, Windows}
import io.github.alexarchambault.windowsansi.WindowsAnsi
import shapeless._

import scala.util.control.NonFatal

object Coursier extends CommandAppPreA(Parser[LauncherOptions], Help[LauncherOptions], CoursierCommand.parser, CoursierCommand.help) {

  val isGraalvmNativeImage = sys.props.contains("org.graalvm.nativeimage.imagecode")

  if (System.console() != null && Windows.isWindows)
    try WindowsAnsi.setup()
    catch {
      case NonFatal(e) =>
        val doThrow = java.lang.Boolean.getBoolean("coursier.windows-ansi.throw-exception")
        if (doThrow || java.lang.Boolean.getBoolean("coursier.windows-ansi.verbose"))
          System.err.println(s"Error setting up Windows terminal for ANSI escape codes: $e")
        if (doThrow)
           throw e
    }

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

  def runA =
    args => {
      case Inl(bootstrapOptions) =>
        Bootstrap.run(bootstrapOptions, args)
      case Inr(Inl(completeOptions)) =>
        Complete.run(completeOptions, args)
      case Inr(Inr(Inl(fetchOptions))) =>
        Fetch.run(fetchOptions, args)
      case Inr(Inr(Inr(Inl(getOptions)))) =>
        Get.run(getOptions, args)
      case Inr(Inr(Inr(Inr(Inl(installOptions))))) =>
        Install.run(installOptions, args)
      case Inr(Inr(Inr(Inr(Inr(Inl(javaOptions)))))) =>
        Java.run(javaOptions, args)
      case Inr(Inr(Inr(Inr(Inr(Inr(Inl(javaHomeOptions))))))) =>
        JavaHome.run(javaHomeOptions, args)
      case Inr(Inr(Inr(Inr(Inr(Inr(Inr(Inl(launchOptions)))))))) =>
        Launch.run(launchOptions, args)
      case Inr(Inr(Inr(Inr(Inr(Inr(Inr(Inr(Inl(listOptions))))))))) =>
        List.run(listOptions, args)
      case Inr(Inr(Inr(Inr(Inr(Inr(Inr(Inr(Inr(Inl(publishOptions)))))))))) =>
        Publish.run(publishOptions, args)
      case Inr(Inr(Inr(Inr(Inr(Inr(Inr(Inr(Inr(Inr(Inl(resolveOptions))))))))))) =>
        Resolve.run(resolveOptions, args)
      case Inr(Inr(Inr(Inr(Inr(Inr(Inr(Inr(Inr(Inr(Inr(Inl(setupOptions)))))))))))) =>
        Setup.run(setupOptions, args)
      case Inr(Inr(Inr(Inr(Inr(Inr(Inr(Inr(Inr(Inr(Inr(Inr(Inl(uninstallOptions))))))))))))) =>
        Uninstall.run(uninstallOptions, args)
      case Inr(Inr(Inr(Inr(Inr(Inr(Inr(Inr(Inr(Inr(Inr(Inr(Inr(Inl(updateOptions)))))))))))))) =>
        Update.run(updateOptions, args)
      case Inr(Inr(Inr(Inr(Inr(Inr(Inr(Inr(Inr(Inr(Inr(Inr(Inr(Inr(cnil)))))))))))))) =>
        cnil.impossible
    }

}
