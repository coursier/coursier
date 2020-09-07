package coursier.cli

import java.nio.charset.Charset

import coursier.cache.internal.FileUtil
import utest._
import java.util.concurrent.TimeUnit
import java.io.File

object LaunchTests extends TestSuite {

  private lazy val launcher = sys.props.getOrElse(
    "coursier-test-launcher",
    sys.error("Java property coursier-test-launcher not set")
  )

  private def output(args: Seq[String], keepErrorOutput: Boolean): String = {
    var p: Process = null
    try {
      p = new ProcessBuilder(Seq(launcher, "launch") ++ args: _*)
        .inheritIO()
        .redirectOutput(ProcessBuilder.Redirect.PIPE)
        .redirectErrorStream(keepErrorOutput)
        .start()
      new String(FileUtil.readFully(p.getInputStream), Charset.defaultCharset())
    } finally {
      if (p != null) {
        val exited = p.waitFor(1L, TimeUnit.SECONDS)
        if (!exited)
          p.destroy()
      }
    }
  }

  private def output(args: String*): String =
    output(args, keepErrorOutput = false)

  val tests = Tests {
    test("fork") {
      val output0 = output("--fork", "io.get-coursier:echo:1.0.1", "--", "foo")
      val expectedOutput = "foo" + System.lineSeparator()
      assert(output0 == expectedOutput)
    }

    test("non static main class") {
      val output0 = output(
        args = Seq("org.scala-lang:scala-compiler:2.13.0", "--main-class", "scala.tools.nsc.Driver"),
        keepErrorOutput = true
      )
      val expectedInOutput = "Main method in class scala.tools.nsc.Driver is not static"
      assert(output0.contains(expectedInOutput))
    }

    test("java class path in expansion from launch") {
      import coursier.dependencyString
      val output0 = output("--property", s"foo=$${java.class.path}", "io.get-coursier:props:1.0.2", "--", "foo")
      val expected = coursier.Fetch()
        .addDependencies(dep"io.get-coursier:props:1.0.2")
        .run()
        .map(_.getAbsolutePath)
        .mkString(File.pathSeparator) + System.lineSeparator()
      assert(output0 == expected)
    }

    test("inline app") {
      val output0 = output("""{"dependencies": ["io.get-coursier:echo:1.0.1"], "repositories": ["central"]}""", "--", "foo")
      val expected = "foo" + System.lineSeparator()
      assert(output0 == expected)
    }

    test("inline app with id") {
      val output0 = output("""echo:{"dependencies": ["io.get-coursier:echo:1.0.1"], "repositories": ["central"]}""", "--", "foo")
      val expected = "foo" + System.lineSeparator()
      assert(output0 == expected)
    }
  }
}
