package coursier.clitests

import java.io.File

import utest._

abstract class LauncherTests extends TestSuite {

  def launcher: String

  val tests = Tests {
    test("require lower version") {
      val retCode = LauncherTestUtil.tryRun(
        args = Seq(launcher, "--require", "1.0.3"),
        directory = new File(".")
      )
      assert(retCode == 0)
    }

    test("require higher version") {
      val retCode = LauncherTestUtil.tryRun(
        args = Seq(launcher, "--require", "41.0.3"),
        directory = new File(".")
      )
      assert(retCode != 0)
    }
  }
}
