package coursierbuild.modules

import mill.*
import mill.scalalib.*

trait CsScalaTests extends CsTests with ScalaModule {
  def scalacOptions = Task {
    CsScalaTests.removeReleaseOption(super.scalacOptions(), scalaVersion())
  }
}

object CsScalaTests {

  /** Drops `--release`, so that tests can use any API of the JDK they run on.
    *
    * On Scala 3, `--release` is turned into `-java-output-version` instead: that keeps the class
    * file version, and `-Yfuture-lazy-vals` (see [[CsScalaModule]]) requires an explicit output
    * version, but doesn't hide APIs.
    */
  def removeReleaseOption(scalacOptions: Seq[String], scalaVersion: String): Seq[String] = {
    val releaseIdx = scalacOptions.indexOf("--release")
    if (releaseIdx < 0)
      scalacOptions
    else if (scalaVersion.startsWith("3."))
      scalacOptions.updated(releaseIdx, "-java-output-version")
    else
      scalacOptions.take(releaseIdx) ++ scalacOptions.drop(releaseIdx + 2)
  }
}
