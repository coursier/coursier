package coursier.cli.resolve

import java.io.PrintStream

import coursier.cli.params.OutputParams
import coursier.core.{Dependency, Resolution}
import coursier.graph.Conflict
import coursier.params.ResolutionParams
import coursier.parse.{JavaOrScalaDependency, JavaOrScalaModule}
import coursier.util.{ModuleMatcher, Print}

object Output {

  private val nl = sys.props("line.separator")

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
        s"  Dependencies:$nl" +
          Print.dependenciesUnknownConfigs0(
            deps,
            Map.empty,
            printExclusions = outputParams.verbosity >= 2
          )
      )

      if (resolutionParams.forceVersion0.nonEmpty) {
        stderr.println("  Force versions:")
        val ordered = resolutionParams.forceVersion0
          .toVector
          .sortBy { case (mod, _) =>
            mod.toString
          }
        for ((mod, ver) <- ordered)
          stderr.println(s"$mod:${ver.asString}")
      }
    }

  def printResolutionResult(
    printResultStdout: Boolean,
    params: ResolveParams,
    scalaVersionOpt: Option[String],
    platformOpt: Option[String],
    res: Resolution,
    stdout: PrintStream,
    stderr: PrintStream,
    colors: Boolean
  ): Unit =
    if (printResultStdout || params.output.verbosity >= 1 || params.anyTree || params.conflicts) {
      val printHeader = (printResultStdout && params.output.verbosity >= 1) ||
        params.output.verbosity >= 2 ||
        params.anyTree
      if (printHeader)
        stderr.println(s"  Result:")

      val withExclusions = params.output.verbosity >= 1

      val depsStr =
        if (params.whatDependsOn.nonEmpty) {
          val matchers = params.whatDependsOn
            .map(_.module(
              JavaOrScalaModule.scalaBinaryVersion(scalaVersionOpt.getOrElse("")),
              scalaVersionOpt.getOrElse("")
            ))
            .map(ModuleMatcher(_))
          Print.dependencyTree0(
            res,
            roots = res.minDependencies
              .filter(f => matchers.exists(m => m.matches(f.module)))
              .toSeq,
            printExclusions = withExclusions,
            reverse = true,
            colors = colors
          )
        }
        else if (params.reverseTree || params.tree)
          Print.dependencyTree0(
            res,
            printExclusions = withExclusions,
            reverse = params.reverseTree,
            colors = colors
          )
        else if (params.conflicts) {
          val conflicts = Conflict(res)
          val messages  = Print.conflicts(conflicts)
          if (messages.isEmpty) {
            if ((printResultStdout && params.output.verbosity >= 1) || params.output.verbosity >= 2)
              stderr.println("No conflict found.")
            ""
          }
          else
            messages.mkString(nl)
        }
        else if (params.candidateUrls) {
          val classpathOrder = params.classpathOrder.getOrElse(true)
          // TODO Allow to filter on classifiers / attributes / artifact types
          val urls = res.dependencyArtifacts0(None, None, classpathOrder).map(_._3.url)
          urls.mkString(nl)
        }
        else {
          val classpathOrder = params.classpathOrder.getOrElse(false)
          Print.dependenciesUnknownConfigs0(
            if (classpathOrder)
              res.orderedDependencies
            else {
              val set =
                if (res.errors0.nonEmpty || !res.isDone) res.minimizedDependenciesLoose()
                else res.minDependencies
              set.toVector
            },
            res.projectCache0.map { case ((m, v), (_, p)) => ((m, v), p) },
            printExclusions = withExclusions,
            reorder = !classpathOrder
          )
        }

      if (depsStr.nonEmpty)
        if (printResultStdout)
          stdout.println(depsStr)
        else
          stderr.println(depsStr)
    }

}
