package coursierbuild.modules

import java.io.File
import com.github.lolgab.mill.mima.Mima
import coursierbuild.Deps.{Deps, ScalaVersions}
import coursier.version.Version

import mill.*
import mill.api.*
import mill.scalalib.*
import mill.scalajslib.*

import java.util.Locale

import scala.util.Properties

trait CsMima extends Mima with PublishModule {
  def mimaPreviousVersions: T[Seq[String]] = Task {
    val previous = CsMima.mimaPreviousVersions()
    // Scala 3 artifacts are only published from 2.1.25 on, so there is nothing to check
    // binary compatibility against before that. Scala 3 is the only cross value whose
    // artifacts carry a `_3` suffix; anything else uses a Scala 2 (`_2.13` / `_2.12`) suffix
    // or none.
    if (artifactId().endsWith("_3")) {
      val cutOff = Version("2.1.25")
      previous.filter(Version(_) >= cutOff)
    }
    else
      previous
  }

  // mill-mima's default `mimaPreviousArtifacts` throws when there are no previous versions
  // ("No previous artifacts configured"). Reconstruct the artifacts from `mimaPreviousVersions`
  // directly instead, so that an empty list simply yields no artifacts and
  // `mimaReportBinaryIssues` runs as a no-op (nothing to compare against) rather than failing.
  // (Referencing `super.mimaPreviousArtifacts` would not work: Mill evaluates it as an upstream
  // task regardless of the runtime branch.)
  def mimaPreviousArtifacts = Task {
    val versions     = mimaPreviousVersions()
    val organization = pomSettings().organization
    val artifactId0  = artifactId()
    versions.map(version => mvn"$organization:$artifactId0:$version")
  }
}

object CsMima extends ExternalModule {
  def mimaPreviousVersions: T[Seq[String]] = Task.Input {
    // FIXME Print stderr if command fails
    val current = os.proc("git", "describe", "--tags", "--match", "v*")
      .call(stderr = os.Pipe)
      .out.trim()
    // FIXME Print stderr if command fails
    os.proc("git", "tag", "-l")
      .call(stderr = os.Pipe)
      .out.lines()
      .filter(_ != current)
      .filter(_.startsWith("v"))
      .filter(!_.contains("-"))
      .map(_.stripPrefix("v"))
      .filter(!_.startsWith("0."))
      .filter(!_.startsWith("1."))
      .filter(!_.startsWith("2.0.")) // 2.1.x broke binary compatibility with 2.0.x
      .map(Version(_))
      .sorted
      .map(_.repr)
  }

  lazy val millDiscover: mill.api.Discover = mill.api.Discover[this.type]
}
