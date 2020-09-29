package coursier.clitests

import java.io.File

import utest._

abstract class LaunchTests extends TestSuite {

  def launcher: String

  val tests = Tests {
    test("fork") {
      val output = LauncherTestUtil.output(launcher, "launch", "--fork", "io.get-coursier:echo:1.0.1", "--", "foo")
      val expectedOutput = "foo" + System.lineSeparator()
      assert(output == expectedOutput)
    }

    test("non static main class") {
      val output = LauncherTestUtil.output(
        args = Seq(launcher, "launch", "--fork", "org.scala-lang:scala-compiler:2.13.0", "--main-class", "scala.tools.nsc.Driver",  "--property", "user.language=en", "--property", "user.country=US"),
        keepErrorOutput = true
      )
      val expectedInOutput = Seq(
        "Main method",
        "in class scala.tools.nsc.Driver",
        "is not static",
      )
      assert(expectedInOutput.forall(output.contains))
    }

    test("java class path in expansion from launch") {
      import coursier.dependencyString
      val output = LauncherTestUtil.output(launcher, "launch", "--property", s"foo=$${java.class.path}", TestUtil.propsDepStr, "--", "foo")
      val expected = TestUtil.propsCp.mkString(File.pathSeparator) + System.lineSeparator()
      assert(output == expected)
    }

    def inlineApp(): Unit = {
      val output = LauncherTestUtil.output(launcher, "launch", """{"dependencies": ["io.get-coursier:echo:1.0.1"], "repositories": ["central"]}""", "--", "foo")
      val expected = "foo" + System.lineSeparator()
      assert(output == expected)
    }
    test("inline app") {
      if (LauncherTestUtil.isWindows) "disabled"
      else { inlineApp(); "" }
    }

    def inlineAppWithId(): Unit = {
      val output = LauncherTestUtil.output(launcher, "launch", """echo:{"dependencies": ["io.get-coursier:echo:1.0.1"], "repositories": ["central"]}""", "--", "foo")
      val expected = "foo" + System.lineSeparator()
      assert(output == expected)
    }
    test("inline app with id") {
      if (LauncherTestUtil.isWindows) "disabled"
      else { inlineAppWithId(); "" }
    }
  }
}
