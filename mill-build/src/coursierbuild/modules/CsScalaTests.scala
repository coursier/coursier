package coursierbuild.modules

import mill.*
import mill.scalalib.*

trait CsScalaTests extends CsTests with ScalaModule {
  def scalacOptions = Task {
    val baseOptions = super.scalacOptions()
    val releaseIdx  = baseOptions.indexOf("--release")
    if (releaseIdx >= 0)
      baseOptions.take(releaseIdx) ++ baseOptions.drop(releaseIdx + 2)
    else
      baseOptions
  }
}
