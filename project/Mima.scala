
import sbt.Keys._

import com.typesafe.tools.mima.plugin.MimaKeys._
import org.portablescala.sbtplatformdeps.PlatformDepsPlugin.autoImport._

import scala.sys.process._

object Mima {

  def stable(ver: String): Boolean =
    ver
      .replace("-RC", "-")
      .forall(c => c == '.' || c == '-' || c.isDigit)

  def binaryCompatibilityVersions: Set[String] = {

    val latest = Seq("git", "describe", "--tags", "--abbrev=0", "--match", "v*")
      .!!
      .trim
      .stripPrefix("v")

    assert(latest.nonEmpty, "Could not find latest version")

    if (stable(latest)) {
      val prefix = latest.split('.').take(2).map(_ + ".").mkString

      val head = Seq("git", "tag", "--list", "v" + prefix + "*", "--contains", "HEAD")
        .!!
        .linesIterator
        .map(_.trim.stripPrefix("v"))
        .filter(stable)
        .toSet

      val previous = Seq("git", "tag", "--list", "v" + prefix + "*")
        .!!
        .linesIterator
        .map(_.trim.stripPrefix("v"))
        .filter(stable)
        .toSet

      assert(previous.contains(latest), "Something went wrong")

      previous -- head
    } else
      Set()
  }


  lazy val previousArtifacts = Seq(
    mimaPreviousArtifacts := {
      val sv = scalaVersion.value
      val versions = binaryCompatibilityVersions
      val versions0 =
        if (sv.startsWith("2.13.")) versions.filter(_ != "2.0.0-RC1")
        else versions
      versions0.map { ver =>
        organization.value %%% moduleName.value % ver
      }
    }
  )

  lazy val coreFilters = {
    mimaBinaryIssueFilters ++= {
      import com.typesafe.tools.mima.core._

      Seq(
        // ignore shaded-stuff related errors
        (pb: Problem) => pb.matchName.forall(!_.startsWith("coursier.shaded.")),
        (pb: Problem) => pb.matchName.forall(!_.startsWith("coursier.util.shaded.")),
        // was private, now removed
        ProblemFilters.exclude[MissingClassProblem]("coursier.ivy.PropertiesPattern$Parser$"),
        // made private so that the shaded fastparse stuff doesn't leak
        ProblemFilters.exclude[DirectMissingMethodProblem]("coursier.ivy.PropertiesPattern.parser"),
        // corresponds to a default value of a private method, not sure why this error is raised
        ProblemFilters.exclude[IncompatibleResultTypeProblem]("coursier.maven.Pom.coursier$maven$Pom$$module$default$2")
      )
    }
  }

  lazy val cacheFilters = {
    mimaBinaryIssueFilters ++= {
      import com.typesafe.tools.mima.core._

      Seq(
        // these are private, don't know why they end-up appearing here
        // (probably related to https://github.com/typesafehub/migration-manager/issues/34)
        (pb: Problem) => pb.matchName.forall(!_.startsWith("coursier.TermDisplay#DownloadInfo")),
        (pb: Problem) => pb.matchName.forall(!_.startsWith("coursier.TermDisplay$DownloadInfo")),
        (pb: Problem) => pb.matchName.forall(!_.startsWith("coursier.TermDisplay#CheckUpdateInfo")),
        (pb: Problem) => pb.matchName.forall(!_.startsWith("coursier.TermDisplay#Info"))
      )
    }
  }

}
