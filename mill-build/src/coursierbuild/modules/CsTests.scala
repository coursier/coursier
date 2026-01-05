package coursierbuild.modules

import coursierbuild.Deps.Deps
import mill._, mill.scalalib._

trait CsTests extends TestModule with JavaModule {
  def mvnDeps = super.mvnDeps() ++ Seq(
    Deps.pprint,
    Deps.utest
  )
  def testFramework = "utest.runner.Framework"

  def defaultTask() = super[TestModule].defaultTask()
}
