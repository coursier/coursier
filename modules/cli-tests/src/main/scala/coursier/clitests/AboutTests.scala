package coursier.clitests

import utest._

abstract class AboutTests extends TestSuite with LauncherOptions {

  def launcher: String
  def isNative: Boolean

  val tests = Tests {
    test("simple") {
      val lines                = os.proc(launcher, "about").call().out.lines()
      val expectedLauncherType = if (isNative) "native" else "jar"
      val expectedLine         = s"Launcher type: $expectedLauncherType"
      assert(lines.contains(expectedLine))
    }
  }

}
