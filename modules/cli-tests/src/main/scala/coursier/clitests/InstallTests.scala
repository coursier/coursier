package coursier.clitests

import java.io.File

import utest._

import scala.util.Properties

abstract class InstallTests extends TestSuite {

  def launcher: String

  def overrideProguarded: Option[Boolean] =
    None

  private val extraOptions =
    overrideProguarded match {
      case None        => Nil
      case Some(value) => Seq(s"--proguarded=$value")
    }

  val tests = Tests {

    def inlineApp(): Unit =
      TestUtil.withTempDir { tmpDir =>
        LauncherTestUtil.run(
          args = Seq(
            launcher,
            "install",
            "--install-dir",
            tmpDir.getAbsolutePath,
            """echo:{"dependencies": ["io.get-coursier:echo:1.0.1"], "repositories": ["central"]}"""
          ) ++ extraOptions,
          directory = tmpDir
        )
        val output = LauncherTestUtil.output(
          Seq(new File(tmpDir, "echo").getAbsolutePath, "foo"),
          keepErrorOutput = false,
          directory = tmpDir
        )
        val expectedOutput = "foo" + System.lineSeparator()
        assert(output == expectedOutput)
      }
    test("inline app") {
      if (Properties.isWin) "disabled"
      else { inlineApp(); "" }
    }

    def jnaPython(): Unit =
      TestUtil.withTempDir { tmpDir =>
        LauncherTestUtil.run(
          args = Seq(
            launcher,
            "install",
            "--install-dir",
            tmpDir.getAbsolutePath,
            s"""props:{"dependencies": ["${TestUtil.propsDepStr}"], "repositories": ["central"], "jna": ["python"]}"""
          ) ++ extraOptions,
          directory = tmpDir
        )
        val propsLauncher = new File(tmpDir, "props").getAbsolutePath

        val jnaNosysOutput = LauncherTestUtil.output(
          Seq(propsLauncher, "jna.nosys"),
          keepErrorOutput = false,
          directory = tmpDir
        )
        val expectedJnaNosysOutput = "false" + System.lineSeparator()
        assert(jnaNosysOutput == expectedJnaNosysOutput)

        val jnaLibraryPathOutput = LauncherTestUtil.output(
          Seq(propsLauncher, "jna.library.path"),
          keepErrorOutput = false,
          directory = tmpDir
        )
        assert(jnaLibraryPathOutput.trim.nonEmpty)
      }
    test("JNA Python") {
      if (Properties.isWin) "disabled"
      else { jnaPython(); "" }
    }
  }
}
