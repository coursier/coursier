package coursier.clitests

import utest._

abstract class SetupTests extends TestSuite {

  def launcher: String

  val tests = Tests {

    test("setup") {
      TestUtil.withTempDir { tempDir =>
        val result = os.proc(
          launcher,
          "setup",
          "--yes",
          "--install-dir",
          tempDir.getAbsolutePath
        ).call(cwd = os.Path(tempDir, os.pwd))
        assert(result.exitCode == 0)
      }
    }

  }

}
