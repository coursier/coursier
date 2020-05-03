
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

      val previous = Seq("git", "tag", "--list", "v" + prefix + "*", "--contains", "9c27d22ae")
        .!!
        .linesIterator
        .map(_.trim.stripPrefix("v"))
        .filter(stable)
        .toSet

      assert(previous.contains(latest) || latest == "2.0.0-RC6-7", "Something went wrong")

      previous -- head
    } else
      Set()
  }


  lazy val previousArtifacts = Seq(
    mimaPreviousArtifacts := {
      val versions = binaryCompatibilityVersions
        .filter(_ != "2.0.0-RC3-4")
        .filter(_ != "2.0.0-RC4")
        .filter(_ != "2.0.0-RC4-1")
      versions.map { ver =>
        organization.value %%% moduleName.value % ver
      }
    }
  )

  // until 2.0 final, mima is just there to check that we don't break too many things or unexpected stuff
  lazy val utilFilters = {
    mimaBinaryIssueFilters ++= {
      import com.typesafe.tools.mima.core._

      Seq(
        // ignore shaded-stuff related errors
        (pb: Problem) => pb.matchName.forall(!_.startsWith("coursier.util.shaded."))
      )
    }
  }

  lazy val coreFilters = {
    mimaBinaryIssueFilters ++= {
      import com.typesafe.tools.mima.core._

      Seq(
        // false positives, coursier.core.DependencySet#Sets is private
        ProblemFilters.exclude[IncompatibleMethTypeProblem]("coursier.core.DependencySet#Sets.copy"),
        ProblemFilters.exclude[IncompatibleResultTypeProblem]("coursier.core.DependencySet#Sets.copy$default$1"),
        ProblemFilters.exclude[IncompatibleMethTypeProblem]("coursier.core.DependencySet#Sets.this"),
        ProblemFilters.exclude[IncompatibleMethTypeProblem]("coursier.core.DependencySet#Sets.apply"),
        ProblemFilters.exclude[IncompatibleResultTypeProblem]("coursier.core.DependencySet#Sets.required"),
        // ignore shaded-stuff related errors
        (pb: Problem) => pb.matchName.forall(!_.startsWith("coursier.core.shaded."))
      )
    }
  }

  lazy val cacheFilters = {
    mimaBinaryIssueFilters ++= {
      import com.typesafe.tools.mima.core._

      Seq(
        // ignore shaded-stuff related errors
        (pb: Problem) => pb.matchName.forall(!_.startsWith("coursier.cache.shaded."))
      )
    }
  }

  lazy val coursierFilters = {
    mimaBinaryIssueFilters ++= {
      import com.typesafe.tools.mima.core._

      Seq(
        // ignore shaded-stuff related errors
        (pb: Problem) => pb.matchName.forall(!_.startsWith("coursier.internal.shaded."))
      )
    }
  }

  lazy val catsInteropFilters = {
    import com.typesafe.tools.mima.core._
    import com.typesafe.tools.mima.core.ProblemFilters._

    mimaBinaryIssueFilters ++= Seq(
    )
  }

}
