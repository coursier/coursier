package coursier.clitests

import utest._

abstract class SetupTests extends TestSuite {

  def launcher: String

  val tests = Tests {

    test("setup") {
      TestUtil.withTempDir { tempDir =>
        val homeDir    = os.Path(tempDir, os.pwd)
        val installDir = homeDir / "bin"
        val result = os.proc(
          launcher,
          "setup",
          "--yes",
          "--user-home",
          homeDir.toString,
          "--install-dir",
          installDir.toString
        ).call()
        assert(result.exitCode == 0)

        for (app <- List("scala", "sbt", "cs"))
          assert(os.exists(installDir / app))
      }
    }

  }

}
