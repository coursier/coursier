package coursierbuild.modules

import coursierbuild.Deps.Deps
import mill._, mill.scalalib._, mill.scalajslib._

trait CsTests extends TestModule with JavaModule {
  // os-lib isn't published for Scala.js, only pull it in on the JVM (and Scala Native)
  private def maybeOsLib: Seq[Dep] =
    this match {
      case _: ScalaJSModule => Nil
      case _                => Seq(Deps.osLib)
    }
  def mvnDeps = super.mvnDeps() ++ maybeOsLib ++ Seq(
    Deps.pprint,
    Deps.utest
  )
  def testFramework = "utest.runner.Framework"

  def defaultTask() = super[TestModule].defaultTask()
}
