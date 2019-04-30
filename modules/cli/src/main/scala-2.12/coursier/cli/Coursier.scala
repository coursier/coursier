package coursier.cli

import java.io.InputStream
import java.nio.charset.StandardCharsets

import caseapp.core.help.Help
import caseapp.core.parser.Parser
import coursier.bootstrap.util.FileUtil
import coursier.cli.complete.Complete
import coursier.cli.fetch.Fetch
import coursier.cli.launch.Launch
import coursier.cli.options.LauncherOptions
import coursier.cli.resolve.Resolve
import shapeless._

object Coursier extends CommandAppPreA(Parser[LauncherOptions], Help[LauncherOptions], CoursierCommand.parser, CoursierCommand.help) {

  override val appName = "Coursier"
  override val progName = "coursier"
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

  def beforeCommand(options: LauncherOptions, remainingArgs: Seq[String]): Unit =
    options.completions.foreach {
      case "zsh" =>
        System.out.print(zshCompletions())
        sys.exit(0)
      case other =>
        System.err.println(s"Unrecognized or unsupported shell: $other")
        sys.exit(1)
    }

  def runA =
    args => {
      case Inl(bootstrapOptions) =>
        Bootstrap.run(bootstrapOptions, args)
      case Inr(Inl(completeOptions)) =>
        Complete.run(completeOptions, args)
      case Inr(Inr(Inl(fetchOptions))) =>
        Fetch.run(fetchOptions, args)
      case Inr(Inr(Inr(Inl(launchOptions)))) =>
        Launch.run(launchOptions, args)
      case Inr(Inr(Inr(Inr(Inl(resolveOptions))))) =>
        Resolve.run(resolveOptions, args)
      case Inr(Inr(Inr(Inr(Inr(Inl(sparkSubmitOptions)))))) =>
        SparkSubmit.run(sparkSubmitOptions, args)
      case Inr(Inr(Inr(Inr(Inr(Inr(cnil)))))) =>
        cnil.impossible
    }

}
