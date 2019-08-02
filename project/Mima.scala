
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

  // until 2.0 final, mima is just there to check that we don't break too many things or unexpected stuff
  lazy val coreFilters = {
    mimaBinaryIssueFilters ++= {
      import com.typesafe.tools.mima.core._

      Seq(
        // renamed
        ProblemFilters.exclude[DirectMissingMethodProblem]("coursier.core.Resolution.dependenciesWithSelectedVersions"),
        // tweaked default values / overloads
        ProblemFilters.exclude[DirectMissingMethodProblem]("coursier.core.Resolution.dependencyArtifacts$default$1"),
        ProblemFilters.exclude[DirectMissingMethodProblem]("coursier.core.Resolution.artifacts$default$2"),
        ProblemFilters.exclude[DirectMissingMethodProblem]("coursier.core.Resolution.dependenciesOf$default$2"),
        ProblemFilters.exclude[DirectMissingMethodProblem]("coursier.core.Resolution.artifacts$default$1"),
        // extra param…
        ProblemFilters.exclude[DirectMissingMethodProblem]("coursier.util.Print.dependenciesUnknownConfigs"),
        // things that are private now
        ProblemFilters.exclude[IncompatibleMethTypeProblem]("coursier.core.ResolutionProcess.fetchOne"),
        ProblemFilters.exclude[IncompatibleResultTypeProblem]("coursier.core.compatibility.package.listWebPageRawElements"),
        ProblemFilters.exclude[IncompatibleResultTypeProblem]("coursier.util.WebPage.listFiles"),
        ProblemFilters.exclude[DirectMissingMethodProblem]("coursier.util.WebPage.listElements"),
        ProblemFilters.exclude[IncompatibleResultTypeProblem]("coursier.util.WebPage.listDirectories"),
        // should have been private
        ProblemFilters.exclude[DirectMissingMethodProblem]("coursier.core.Resolution.defaultConfiguration"),
        ProblemFilters.exclude[DirectMissingMethodProblem]("coursier.core.Resolution.withDefaultConfig"),
        ProblemFilters.exclude[DirectMissingMethodProblem]("coursier.core.Resolution.finalDependencies"),
        // better constructor for later bin compat
        ProblemFilters.exclude[DirectMissingMethodProblem]("coursier.core.Resolution.this"),
        // made non-case classes, for easier preserving of bin compat later
        (pb: Problem) => pb.matchName.forall(!_.startsWith("coursier.maven.MavenRepository")),
        (pb: Problem) => pb.matchName.forall(!_.startsWith("coursier.ivy.IvyRepository")),
        // for 2.11
        ProblemFilters.exclude[ReversedMissingMethodProblem]("coursier.core.Repository.versionsCheckHasModule"),
        ProblemFilters.exclude[ReversedMissingMethodProblem]("coursier.core.Repository.fetchVersions"),
        ProblemFilters.exclude[ReversedMissingMethodProblem]("coursier.core.Repository#Complete.sbtAttrStub"),
        ProblemFilters.exclude[ReversedMissingMethodProblem]("coursier.core.Repository#Complete.hasModule$default$2"),
        // should have been private
        ProblemFilters.exclude[DirectMissingMethodProblem]("coursier.ivy.IvyRepository.findNoInverval"),
        ProblemFilters.exclude[DirectMissingMethodProblem]("coursier.maven.MavenRepository.findNoInterval"),
        // for 2.11
        ProblemFilters.exclude[ReversedMissingMethodProblem]("coursier.core.Repository.findMaybeInterval"),
        // added methods (filters for 2.11)
        ProblemFilters.exclude[ReversedMissingMethodProblem]("coursier.core.Repository.repr"),
        ProblemFilters.exclude[ReversedMissingMethodProblem]("coursier.core.Repository#Complete.hasModule"),
        // more or less internal stuff now
        ProblemFilters.exclude[DirectMissingMethodProblem]("coursier.core.ResolutionProcess.fetch"),
        ProblemFilters.exclude[DirectMissingMethodProblem]("coursier.core.ResolutionProcess.fetchOne"),
        // was changed from case class to non case class (for easier bin compat in the future)
        (pb: Problem) => pb.matchName.forall(!_.startsWith("coursier.core.Done")),
        (pb: Problem) => pb.matchName.forall(!_.startsWith("coursier.core.Continue")),
        (pb: Problem) => pb.matchName.forall(!_.startsWith("coursier.core.Missing")),
        // made non-case class, for easier preserving of bin compat later
        ProblemFilters.exclude[MissingTypesProblem]("coursier.core.Resolution"),
        ProblemFilters.exclude[DirectMissingMethodProblem]("coursier.core.Resolution.productElement"),
        ProblemFilters.exclude[DirectMissingMethodProblem]("coursier.core.Resolution.productArity"),
        ProblemFilters.exclude[DirectMissingMethodProblem]("coursier.core.Resolution.canEqual"),
        ProblemFilters.exclude[DirectMissingMethodProblem]("coursier.core.Resolution.productIterator"),
        ProblemFilters.exclude[DirectMissingMethodProblem]("coursier.core.Resolution.productPrefix"),
        ProblemFilters.exclude[DirectMissingMethodProblem]("coursier.core.Resolution.productElementName"),
        ProblemFilters.exclude[DirectMissingMethodProblem]("coursier.core.Resolution.productElementNames"),
        ProblemFilters.exclude[MissingTypesProblem]("coursier.core.Resolution$"),
        ProblemFilters.exclude[DirectMissingMethodProblem]("coursier.core.Resolution.unapply"),
        // mmmhh (2.11 only)
        ProblemFilters.exclude[ReversedMissingMethodProblem]("coursier.core.Repository.versions"),
        // should have been private
        ProblemFilters.exclude[DirectMissingMethodProblem]("coursier.maven.MavenRepository.versionsArtifact"),
        ProblemFilters.exclude[DirectMissingMethodProblem]("coursier.maven.MavenRepository.versionsArtifact"),
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
        // #1291: Added new field to Info
        ProblemFilters.exclude[DirectMissingMethodProblem]("coursier.core.Info.copy"),
        ProblemFilters.exclude[DirectMissingMethodProblem]("coursier.core.Info.this"),
        ProblemFilters.exclude[DirectMissingMethodProblem]("coursier.core.Info.apply"),
        // ignore shaded-stuff related errors
        (pb: Problem) => pb.matchName.forall(!_.startsWith("coursier.shaded.")),
        (pb: Problem) => pb.matchName.forall(!_.startsWith("coursier.util.shaded.")),
        // https://github.com/coursier/coursier/pull/1293
        ProblemFilters.exclude[DirectMissingMethodProblem]("coursier.core.Resolution.merge"),
        ProblemFilters.exclude[DirectMissingMethodProblem]("coursier.core.Resolution.mergeVersions"),
      )
    }
  }

  lazy val cacheFilters = {
    mimaBinaryIssueFilters ++= {
      import com.typesafe.tools.mima.core._

      Seq(
        // Made ArtifactError extends Exception (rather than being an ADT)
        (pb: Problem) => pb.matchName.forall(!_.startsWith("coursier.cache.ArtifactError#")),
        (pb: Problem) => pb.matchName.forall(!_.startsWith("coursier.cache.ArtifactError$")),
        ProblemFilters.exclude[DirectMissingMethodProblem]("coursier.cache.ArtifactError.productIterator"),
        ProblemFilters.exclude[DirectMissingMethodProblem]("coursier.cache.ArtifactError.productPrefix"),
        ProblemFilters.exclude[DirectMissingMethodProblem]("coursier.cache.ArtifactError.productElementName"),
        ProblemFilters.exclude[DirectMissingMethodProblem]("coursier.cache.ArtifactError.productElementNames"),
        ProblemFilters.exclude[MissingTypesProblem]("coursier.cache.ArtifactError"),
        // for 2.11 (???)
        ProblemFilters.exclude[DirectMissingMethodProblem]("coursier.cache.FileCache.coursier$cache$FileCache$$checkFileExists$default$3$1"),
        // Removed private method
        ProblemFilters.exclude[DirectMissingMethodProblem]("coursier.cache.internal.Terminal#Ansi.control$extension"),
        ProblemFilters.exclude[DirectMissingMethodProblem]("coursier.cache.internal.Terminal#Ansi.coursier$cache$internal$Terminal$Ansi$$control$extension"),
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
        // deprecated method, one default value was removed
        ProblemFilters.exclude[DirectMissingMethodProblem]("coursier.package#Resolution.apply$default$1"),
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
