package coursierbuild.modules

import coursierbuild.Deps
import mill._, mill.scalalib._

/** [[CsTests]] with the Scala Native publications of the test dependencies.
  *
  * `Deps.osLib` is the plain JVM artifact - it compiles fine against a Scala Native module but does
  * not link, so it has to be swapped for the cross-published one.
  */
trait CsNativeTests extends CsTests {
  def mvnDeps = Task {
    super.mvnDeps().filter(_ != Deps.osLib) ++ Seq(Deps.osLibCross)
  }
}
