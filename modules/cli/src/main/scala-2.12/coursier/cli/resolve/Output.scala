package coursier.cli.resolve

import java.io.PrintStream

import coursier.cli.params.ResolveParams
import coursier.cli.params.shared.OutputParams
import coursier.core.{Dependency, Resolution}
import coursier.params.ResolutionParams
import coursier.util.Print
import coursier.util.Print.Colors

object Output {

  def errPrintln(s: String) = Console.err.println(s)

  def printDependencies(
    outputParams: OutputParams,
    resolutionParams: ResolutionParams,
    deps: Seq[Dependency],
    stdout: PrintStream,
    stderr: PrintStream
  ): Unit =
    if (outputParams.verbosity >= 1) {
      stderr.println(
        s"  Dependencies:\n" +
          Print.dependenciesUnknownConfigs(
            deps,
            Map.empty,
            printExclusions = outputParams.verbosity >= 2
          )
      )

      if (resolutionParams.forceVersion.nonEmpty) {
        stderr.println("  Force versions:")
        for ((mod, ver) <- resolutionParams.forceVersion.toVector.sortBy { case (mod, _) => mod.toString })
          stderr.println(s"$mod:$ver")
      }
    }

  def printResolutionResult(
    printResultStdout: Boolean,
    params: ResolveParams,
    res: Resolution,
    stdout: PrintStream,
    stderr: PrintStream,
    colors: Boolean
  ): Unit =
    if (printResultStdout || params.output.verbosity >= 1 || params.anyTree) {
      if ((printResultStdout && params.output.verbosity >= 1) || params.output.verbosity >= 2 || params.anyTree)
        stderr.println(s"  Result:")

      val depsStr =
        if (params.whatDependsOn.nonEmpty)
          Print.dependencyTree(
            res,
            roots = res.minDependencies.filter(f => params.whatDependsOn(f.module)).toSeq,
            printExclusions = params.output.verbosity >= 1,
            reverse = true,
            colors = colors
          )
        else if (params.reverseTree || params.tree)
          Print.dependencyTree(
            res,
            printExclusions = params.output.verbosity >= 1,
            reverse = params.reverseTree,
            colors = colors
          )
        else
          Print.dependenciesUnknownConfigs(
            res.minDependencies.toVector,
            res.projectCache.mapValues { case (_, p) => p },
            printExclusions = params.output.verbosity >= 1
          )

      if (printResultStdout)
        stdout.println(depsStr)
      else
        stderr.println(depsStr)
    }

}
