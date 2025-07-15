package coursierbuild.modules

import java.io.File
import com.github.lolgab.mill.mima.Mima
import coursierbuild.Deps.{Deps, ScalaVersions}

import mill._, mill.scalalib._, mill.scalajslib._

import java.util.Locale

import scala.util.Properties

trait CsMima extends Mima {
  def mimaPreviousVersions: T[Seq[String]] = T.input {
    val current = os.proc("git", "describe", "--tags", "--match", "v*")
      .call()
      .out.trim()
    os.proc("git", "tag", "-l")
      .call()
      .out.lines()
      .filter(_ != current)
      .filter(_.startsWith("v"))
      .filter(!_.contains("-"))
      .map(_.stripPrefix("v"))
      .filter(!_.startsWith("0."))
      .filter(!_.startsWith("1."))
      .filter(!_.startsWith("2.0.")) // 2.1.x broke binary compatibility with 2.0.x
      .map(coursier.core.Version(_))
      .sorted
      .map(_.repr)
  }
}
