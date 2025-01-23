package coursier.clitests

import utest._

import scala.util.Properties

abstract class AboutTests extends TestSuite with LauncherOptions {

  def launcher: String
  def assembly: String
  def isNative: Boolean
  def isNativeStatic: Boolean

  def hasDocker: Boolean =
    Properties.isLinux

  def alpineJavaImage =
    sys.props.getOrElse(
      "coursier.test.alpine-java-image",
      sys.error("coursier.test.alpine-java-image Java property not set")
    )
  def alpineImage =
    sys.props.getOrElse(
      "coursier.test.alpine-image",
      sys.error("coursier.test.alpine-image Java property not set")
    )

  val tests = Tests {
    test("simple") {
      val lines                = os.proc(launcher, "about").call().out.lines()
      val expectedLauncherType = if (isNative) "native" else "jar"
      val expectedLine         = s"Launcher type: $expectedLauncherType"
      assert(lines.contains(expectedLine))
    }

    test("alpine-linux") {
      if (hasDocker && !isNative) alpineLinuxTest(isNative = false)
      else if (hasDocker && isNative && isNativeStatic) alpineLinuxTest(isNative = true)
      else "Docker test disabled"
    }

    def alpineLinuxTest(isNative: Boolean): Unit = {

      TestUtil.withTempDir { tmpDir0 =>
        val tmpDir = os.Path(tmpDir0, os.pwd)
        os.copy(
          os.Path(if (isNative) launcher else assembly),
          tmpDir / (if (isNative) "cs" else "cs.jar")
        )

        val command =
          if (isNative)
            Seq(
              "docker",
              "run",
              "--rm",
              "-v",
              s"$tmpDir:/shared",
              alpineImage,
              "/shared/cs",
              "about"
            )
          else
            Seq(
              "docker",
              "run",
              "--rm",
              "-v",
              s"$tmpDir:/shared",
              alpineJavaImage,
              "java",
              "-jar",
              "/shared/cs.jar",
              "about"
            )
        val output = os.proc(command).call(cwd = tmpDir).out.lines()

        val expectedLine = "OS: linux-musl"
        assert(output.contains(expectedLine))
      }
    }
  }

}
