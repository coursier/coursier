import $ivy.`com.typesafe:mima-core_2.13:0.9.2`

import com.typesafe.tools.mima.lib.MiMaLib
import com.typesafe.tools.mima.core._
import mill._, mill.scalalib._

// Originally adapted from https://github.com/com-lihaoyi/mill/blob/3e6da73fa7077e5deef6974821cb7b5bab7ee46c/integration/test/resources/play-json/mima.sc
trait Mima extends ScalaModule with PublishModule {
  def mimaPreviousVersions: T[Seq[String]]
  def mimaBinaryIssueFilters: Seq[ProblemFilter] = Seq.empty

  def mimaPreviousDeps = T {
    Agg.from(mimaPreviousVersions().map { version =>
      ivy"${pomSettings().organization}:${artifactId()}:$version"
    })
  }

  def mimaPreviousArtifacts = T {
    resolveDeps(mimaPreviousDeps)().filter(_.path.segments.contains(artifactId()))
  }

  def mimaBinaryIssues: T[List[(String, List[String])]] = T {
    val currentClassFiles = compile().classes.path
    val classPath         = runClasspath()

    val lib = new MiMaLib(classPath.map(_.path.toIO))

    mimaPreviousArtifacts().toList.map(_.path).map { path =>
      val problems = lib.collectProblems(path.toIO, currentClassFiles.toIO)
      path.toString -> problems.filter { problem =>
        mimaBinaryIssueFilters.forall(_.apply(problem))
      }.map(_.description("current"))
    }
  }

  def mimaReportBinaryIssues(): define.Command[Unit] = T.command {
    val issues   = mimaBinaryIssues()
    var anyIssue = false
    for ((artifact, artifactIssues) <- issues if artifactIssues.nonEmpty) {
      anyIssue = true
      println(s"Found issues compared to $artifact")
      for (issue <- artifactIssues)
        println(issue)
    }
    if (anyIssue)
      sys.error("Found binary issues")
  }
}
