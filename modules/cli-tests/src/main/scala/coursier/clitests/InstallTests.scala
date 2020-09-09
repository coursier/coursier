package coursier.clitests

import java.io.File

import utest._

abstract class InstallTests extends TestSuite {

  def launcher: String

  val tests = Tests {
    test("inline app") {
      TestUtil.withTempDir { tmpDir =>
        LauncherTestUtil.run(
          args = Seq(
            launcher,
            "install",
            "--install-dir", tmpDir.getAbsolutePath,
            """echo:{"dependencies": ["io.get-coursier:echo:1.0.1"], "repositories": ["central"]}"""
          ),
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
    }

    test("env vars") {
      TestUtil.withTempDir { tmpDir =>
        LauncherTestUtil.run(
          args = Seq(
            launcher,
            "install",
            "--install-dir", tmpDir.getAbsolutePath,
            """env:{"dependencies": ["io.get-coursier:env:1.0.4"], "repositories": ["central"]}"""
          ),
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
    }
  }
}
