package coursierbuild.modules

import mill.*
import mill.scalalib.*

trait CsScalaTests extends CsTests with ScalaModule {
  def scalacOptions = Task {
    CsScalaTests.removeReleaseOption(super.scalacOptions())
  }
}

object CsScalaTests {
  def removeReleaseOption(scalacOptions: Seq[String]): Seq[String] = {
    val releaseIdx = scalacOptions.indexOf("--release")
    if (releaseIdx >= 0)
      scalacOptions.take(releaseIdx) ++ scalacOptions.drop(releaseIdx + 2)
    else
      scalacOptions
  }
}
