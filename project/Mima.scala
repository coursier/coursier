
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
        // things that are going to change more before 2.0 final
        ProblemFilters.exclude[IncompatibleResultTypeProblem]("coursier.core.Resolution.copy$default$2"),
        ProblemFilters.exclude[IncompatibleMethTypeProblem]("coursier.core.Resolution.copy"),
        ProblemFilters.exclude[IncompatibleMethTypeProblem]("coursier.core.Resolution.this"),
        ProblemFilters.exclude[IncompatibleMethTypeProblem]("coursier.core.Resolution.apply"),
        // should have been private
        ProblemFilters.exclude[DirectMissingMethodProblem]("coursier.core.Resolution#DepMgmt.add"),
        // things that were supposed to be private
        ProblemFilters.exclude[IncompatibleResultTypeProblem]("coursier.core.Resolution#DepMgmt.key"),
        ProblemFilters.exclude[IncompatibleResultTypeProblem]("coursier.core.Resolution#DepMgmt.key"),
        // was changed from case class to non case class (for easier bin compat in the future)
        (pb: Problem) => pb.matchName.forall(!_.startsWith("coursier.core.Authentication")),
        // was changed from case class to non case class (for easier bin compat in the future)
        ProblemFilters.exclude[MissingTypesProblem]("coursier.core.Dependency$"),
        ProblemFilters.exclude[DirectMissingMethodProblem]("coursier.core.Dependency.unapply"),
        ProblemFilters.exclude[MissingTypesProblem]("coursier.core.Dependency"),
        ProblemFilters.exclude[DirectMissingMethodProblem]("coursier.core.Dependency.productElement"),
        ProblemFilters.exclude[DirectMissingMethodProblem]("coursier.core.Dependency.productArity"),
        ProblemFilters.exclude[DirectMissingMethodProblem]("coursier.core.Dependency.canEqual"),
        ProblemFilters.exclude[DirectMissingMethodProblem]("coursier.core.Dependency.productIterator"),
        ProblemFilters.exclude[DirectMissingMethodProblem]("coursier.core.Dependency.productPrefix"),
        ProblemFilters.exclude[DirectMissingMethodProblem]("coursier.core.Dependency.productElementNames"),
        ProblemFilters.exclude[DirectMissingMethodProblem]("coursier.core.Dependency.productElementName"),
        ProblemFilters.exclude[IncompatibleMethTypeProblem]("coursier.core.Dependency.this"),
        // things made private, that had no reason to be public
        ProblemFilters.exclude[DirectMissingMethodProblem]("coursier.maven.PomParser#State.dependencyExclusionGroupIdOpt"),
        ProblemFilters.exclude[DirectMissingMethodProblem]("coursier.maven.PomParser#State.dependencyExclusionGroupIdOpt_="),
        ProblemFilters.exclude[DirectMissingMethodProblem]("coursier.maven.PomParser.dependencyHandlers"),
        ProblemFilters.exclude[DirectMissingMethodProblem]("coursier.maven.PomParser.handlerMap"),
        ProblemFilters.exclude[DirectMissingMethodProblem]("coursier.maven.PomParser.handlers"),
        ProblemFilters.exclude[DirectMissingMethodProblem]("coursier.maven.PomParser.content"),
        ProblemFilters.exclude[DirectMissingMethodProblem]("coursier.maven.PomParser.propertyHandlers"),
        ProblemFilters.exclude[DirectMissingMethodProblem]("coursier.maven.PomParser.profileHandlers"),
        // Repository doesn't extend Product anymore
        ProblemFilters.exclude[MissingTypesProblem]("coursier.core.Repository"),
        // ignore shaded-stuff related errors
        (pb: Problem) => pb.matchName.forall(!_.startsWith("coursier.shaded.")),
        (pb: Problem) => pb.matchName.forall(!_.startsWith("coursier.util.shaded."))
      )
    }
  }

  lazy val cacheFilters = {
    mimaBinaryIssueFilters ++= {
      import com.typesafe.tools.mima.core._

      Seq(
        // Tweaked more or less internal things
        ProblemFilters.exclude[DirectMissingMethodProblem]("coursier.cache.MockCache.create$default$2"),
        ProblemFilters.exclude[IncompatibleResultTypeProblem]("coursier.cache.MockCache.create$default$4"),
        ProblemFilters.exclude[IncompatibleResultTypeProblem]("coursier.cache.MockCache.create$default$3"),
        ProblemFilters.exclude[IncompatibleMethTypeProblem]("coursier.cache.MockCache.create"),
        ProblemFilters.exclude[DirectMissingMethodProblem]("coursier.cache.CacheUrl.urlConnectionMaybePartial$default$10"),
        ProblemFilters.exclude[DirectMissingMethodProblem]("coursier.cache.CacheUrl.urlConnectionMaybePartial$default$8"),
        ProblemFilters.exclude[DirectMissingMethodProblem]("coursier.cache.CacheUrl.urlConnectionMaybePartial"),
        ProblemFilters.exclude[DirectMissingMethodProblem]("coursier.cache.CacheUrl.urlConnectionMaybePartial$default$7"),
        ProblemFilters.exclude[DirectMissingMethodProblem]("coursier.cache.CacheUrl.urlConnectionMaybePartial$default$11"),
        ProblemFilters.exclude[DirectMissingMethodProblem]("coursier.cache.CacheUrl.urlConnectionMaybePartial$default$9"),
        // Added an optional argument to that one…
        ProblemFilters.exclude[DirectMissingMethodProblem]("coursier.cache.CacheUrl.urlConnection"),
        // these are private, don't know why they end-up appearing here
        // (probably related to https://github.com/typesafehub/migration-manager/issues/34)
        (pb: Problem) => pb.matchName.forall(!_.startsWith("coursier.TermDisplay#DownloadInfo")),
        (pb: Problem) => pb.matchName.forall(!_.startsWith("coursier.TermDisplay$DownloadInfo")),
        (pb: Problem) => pb.matchName.forall(!_.startsWith("coursier.TermDisplay#CheckUpdateInfo")),
        (pb: Problem) => pb.matchName.forall(!_.startsWith("coursier.TermDisplay#Info"))
      )
    }
  }

  lazy val coursierFilters = {
    mimaBinaryIssueFilters ++= {
      import com.typesafe.tools.mima.core._

      Seq(
        // removed some unused default values
        ProblemFilters.exclude[DirectMissingMethodProblem]("coursier.params.CacheParamsHelpers.cache$default$1"),
        ProblemFilters.exclude[DirectMissingMethodProblem]("coursier.params.CacheParamsHelpers.cache$default$2"),
        // InMemoryRepository changed a bit (for easier bin compat in the future)
        ProblemFilters.exclude[DirectMissingMethodProblem]("coursier.util.InMemoryRepository.unapply"),
        ProblemFilters.exclude[DirectMissingMethodProblem]("coursier.util.InMemoryRepository.<init>$default$2"),
        ProblemFilters.exclude[DirectMissingMethodProblem]("coursier.util.InMemoryRepository.apply$default$2"),
        ProblemFilters.exclude[DirectMissingMethodProblem]("coursier.util.InMemoryRepository.productElement"),
        ProblemFilters.exclude[DirectMissingMethodProblem]("coursier.util.InMemoryRepository.productArity"),
        ProblemFilters.exclude[DirectMissingMethodProblem]("coursier.util.InMemoryRepository.copy$default$2"),
        ProblemFilters.exclude[DirectMissingMethodProblem]("coursier.util.InMemoryRepository.canEqual"),
        ProblemFilters.exclude[DirectMissingMethodProblem]("coursier.util.InMemoryRepository.copy"),
        ProblemFilters.exclude[DirectMissingMethodProblem]("coursier.util.InMemoryRepository.copy$default$1"),
        ProblemFilters.exclude[DirectMissingMethodProblem]("coursier.util.InMemoryRepository.productIterator"),
        ProblemFilters.exclude[DirectMissingMethodProblem]("coursier.util.InMemoryRepository.productPrefix"),
        ProblemFilters.exclude[DirectMissingMethodProblem]("coursier.util.InMemoryRepository.this"),
        // ignore shaded-stuff related errors
        (pb: Problem) => pb.matchName.forall(!_.startsWith("coursier.internal.shaded.")),
      )
    }
  }

}
