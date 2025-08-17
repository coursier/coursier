package coursierbuild.modules

import mill._, mill.scalalib._

trait JvmTests extends JavaModule with CsResourcesTests {
  def defaultCommandName() = "test"
}
