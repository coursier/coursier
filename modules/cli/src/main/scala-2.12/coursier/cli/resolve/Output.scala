package coursier.cli.resolve

import coursier.cli.params.ResolveParams
import coursier.cli.params.shared.{OutputParams, ResolutionParams}
import coursier.core.{Dependency, Resolution}
import coursier.util.Print

object Output {

  def errPrintln(s: String) = Console.err.println(s)

  def printDependencies(
    outputParams: OutputParams,
    resolutionParams: ResolutionParams,
    deps: Seq[Dependency]
  ) =
    if (outputParams.verbosity >= 1) {
      errPrintln(
        s"  Dependencies:\n" +
          Print.dependenciesUnknownConfigs(
            deps,
            Map.empty,
            printExclusions = outputParams.verbosity >= 2
          )
      )

      if (resolutionParams.forceVersion.nonEmpty) {
        errPrintln("  Force versions:")
        for ((mod, ver) <- resolutionParams.forceVersion.toVector.sortBy { case (mod, _) => mod.toString })
          errPrintln(s"$mod:$ver")
      }
    }

  def printResolutionResult(
    printResultStdout: Boolean,
    params: ResolveParams,
    dependencies: Seq[Dependency],
    res: Resolution
  ): Unit =
    if (printResultStdout || params.output.verbosity >= 1 || params.tree || params.reverseTree) {
      if ((printResultStdout && params.output.verbosity >= 1) || params.output.verbosity >= 2 || params.tree || params.reverseTree)
        errPrintln(s"  Result:")

      val depsStr =
        if (params.reverseTree || params.tree)
          Print.dependencyTree(
            dependencies,
            res,
            printExclusions = params.output.verbosity >= 1,
            reverse = params.reverseTree
          )
        else
          Print.dependenciesUnknownConfigs(
            res.minDependencies.toVector,
            res.projectCache.mapValues { case (_, p) => p },
            printExclusions = params.output.verbosity >= 1
          )

      if (printResultStdout)
        println(depsStr)
      else
        errPrintln(depsStr)
    }

}
