package coursier.clitests

import java.io.File

import utest._

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
      if (LauncherTestUtil.isWindows) "disabled"
      else { inlineApp(); "" }
    }

    def envVars(): Unit =
      TestUtil.withTempDir { tmpDir =>
        LauncherTestUtil.run(
          args = Seq(
            launcher,
            "install",
            "--install-dir",
            tmpDir.getAbsolutePath,
            """env:{"dependencies": ["io.get-coursier:env:1.0.4"], "repositories": ["central"]}"""
          ) ++ extraOptions,
          directory = tmpDir
        )
        val envLauncher = new File(tmpDir, "env").getAbsolutePath

        val csJvmLauncherOutput = LauncherTestUtil.output(
          Seq(envLauncher, "CS_JVM_LAUNCHER"),
          keepErrorOutput = false,
          directory = tmpDir
        )
        val expectedCsJvmLauncherOutput = "true" + System.lineSeparator()
        assert(csJvmLauncherOutput == expectedCsJvmLauncherOutput)

        val isCsInstalledLauncherOutput = LauncherTestUtil.output(
          Seq(envLauncher, "IS_CS_INSTALLED_LAUNCHER"),
          keepErrorOutput = false,
          directory = tmpDir
        )
        val expectedIsCsInstalledLauncherOutput = "true" + System.lineSeparator()
        assert(isCsInstalledLauncherOutput == expectedIsCsInstalledLauncherOutput)
      }
    test("env vars") {
      if (LauncherTestUtil.isWindows) "disabled"
      else { envVars(); "" }
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
      if (LauncherTestUtil.isWindows) "disabled"
      else { jnaPython(); "" }
    }
  }
}
