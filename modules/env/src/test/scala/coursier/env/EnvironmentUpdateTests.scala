package coursier.env

import utest._

object EnvironmentUpdateTests extends TestSuite {

  val tests = Tests {

    test("leave ordinary values as is") {
      val update = EnvironmentUpdate().copy(
        set = Seq("JAVA_HOME" -> "/foo/jvm/openjdk 17"),
        pathLikeAppends = Seq("PATH" -> "/foo/jvm/openjdk 17/bin")
      )
      val expectedBash =
        """export JAVA_HOME="/foo/jvm/openjdk 17"
          |export PATH="/foo/jvm/openjdk 17/bin:$PATH"""".stripMargin
      val expectedFish =
        """set -x JAVA_HOME "/foo/jvm/openjdk 17"
          |set -x PATH "/foo/jvm/openjdk 17/bin:$PATH"""".stripMargin
      val expectedBat =
        "set \"JAVA_HOME=/foo/jvm/openjdk 17\"\r\nset \"PATH=/foo/jvm/openjdk 17/bin;%PATH%\""
      assert(update.bashScript == expectedBash)
      assert(update.fishScript == expectedFish)
      assert(update.batScript == expectedBat)
    }

    test("escape special characters in bash scripts") {
      for (value <- ShellEval.nastyValues) {
        val update = EnvironmentUpdate().copy(
          set = Seq("JAVA_HOME" -> value),
          pathLikeAppends = Seq("PATH" -> s"$value/bin")
        )
        val script = update.bashScript
        for (shell <- ShellEval.shells) {
          val (javaHome, createdJavaHome) = ShellEval.eval(shell, script, "JAVA_HOME")
          assert(javaHome == value)
          assert(createdJavaHome.isEmpty)
          val (path, createdPath) =
            ShellEval.eval(shell, script, "PATH", Map("PATH" -> "/usr/bin:/bin"))
          assert(path == s"$value/bin:/usr/bin:/bin")
          assert(createdPath.isEmpty)
        }
      }
    }

    test("escape special characters in fish scripts") {
      val update = EnvironmentUpdate().copy(
        set = Seq("JAVA_HOME" -> "/a\\b\"c$d`e(f)"),
        pathLikeAppends = Seq("PATH" -> "/a$(b)")
      )
      val expected =
        """set -x JAVA_HOME "/a\\b\"c\$d`e(f)"
          |set -x PATH "/a\$(b):$PATH"""".stripMargin
      assert(update.fishScript == expected)
    }

    test("reject new lines") {
      for (value <- Seq("/jvm/jdk\ntouch pwned", "/jvm/jdk\rtouch pwned", "/jvm/jdk\u0000")) {
        val update = EnvironmentUpdate().copy(set = Seq("JAVA_HOME" -> value))
        assertThrows[IllegalArgumentException](update.bashScript)
        assertThrows[IllegalArgumentException](update.fishScript)
        assertThrows[IllegalArgumentException](update.batScript)
        val pathUpdate = EnvironmentUpdate().copy(pathLikeAppends = Seq("PATH" -> value))
        assertThrows[IllegalArgumentException](pathUpdate.bashScript)
      }
    }

    test("reject double quotes in batch scripts") {
      val update = EnvironmentUpdate().copy(set = Seq("JAVA_HOME" -> "C:\\jdk\" & calc & \""))
      assertThrows[IllegalArgumentException](update.batScript)
    }

    test("reject invalid variable names") {
      for (name <- Seq("", "A B", "A=B", "1A", "A$(touch pwned)")) {
        val update = EnvironmentUpdate().copy(set = Seq(name -> "/jvm/jdk"))
        assertThrows[IllegalArgumentException](update.bashScript)
      }
    }

    test("quote PowerShell literals") {
      val script = WindowsEnvVarUpdater.setEnvVarScript("JAVA_HOME", "C:\\jdk$(calc)'\u2019\"")
      val expected =
        "[Environment]::SetEnvironmentVariable('JAVA_HOME', 'C:\\jdk$(calc)''\u2019\u2019\"', " +
          "\"User\")\n"
      assert(script == expected)
    }
  }
}
