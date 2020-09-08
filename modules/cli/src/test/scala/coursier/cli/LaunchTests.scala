package coursier.cli

import java.io.File

import utest._

object LaunchTests extends TestSuite {

  private lazy val launcher = sys.props.getOrElse(
    "coursier-test-launcher",
    sys.error("Java property coursier-test-launcher not set")
  )

  val tests = Tests {
    test("fork") {
      val output = LauncherTestUtil.output("launch", "--fork", "io.get-coursier:echo:1.0.1", "--", "foo")
      val expectedOutput = "foo" + System.lineSeparator()
      assert(output == expectedOutput)
    }

    test("non static main class") {
      val output = LauncherTestUtil.output(
        args = Seq("launch", "org.scala-lang:scala-compiler:2.13.0", "--main-class", "scala.tools.nsc.Driver"),
        keepErrorOutput = true
      )
      val expectedInOutput = "Main method in class scala.tools.nsc.Driver is not static"
      assert(output.contains(expectedInOutput))
    }

    test("java class path in expansion from launch") {
      import coursier.dependencyString
      val output = LauncherTestUtil.output("launch", "--property", s"foo=$${java.class.path}", TestUtil.propsDepStr, "--", "foo")
      val expected = TestUtil.propsCp.mkString(File.pathSeparator) + System.lineSeparator()
      assert(output == expected)
    }

    test("inline app") {
      val output = LauncherTestUtil.output("launch", """{"dependencies": ["io.get-coursier:echo:1.0.1"], "repositories": ["central"]}""", "--", "foo")
      val expected = "foo" + System.lineSeparator()
      assert(output == expected)
    }

    test("inline app with id") {
      val output = LauncherTestUtil.output("launch", """echo:{"dependencies": ["io.get-coursier:echo:1.0.1"], "repositories": ["central"]}""", "--", "foo")
      val expected = "foo" + System.lineSeparator()
      assert(output == expected)
    }
  }
}
