package coursierbuild.modules

import java.io.File
import com.github.lolgab.mill.mima.Mima
import coursierbuild.Deps.{Deps, ScalaVersions}

import mill.*
import mill.api.*
import mill.scalalib.*
import mill.scalajslib.*

import java.util.Locale

import scala.util.Properties

trait CsMima extends Mima {
  def mimaPreviousVersions: T[Seq[String]] = Task {
    CsMima.mimaPreviousVersions()
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
      .map(coursier.version.Version(_))
      .sorted
      .map(_.repr)
  }

  lazy val millDiscover: mill.api.Discover = mill.api.Discover[this.type]
}
