package coursier.clitests

import utest._

import scala.util.Properties

abstract class ConfigTests extends TestSuite {

  def launcher: String

  private val password = "1234-secret"

  private def configContent =
    s"""{
       |  "repositories": {
       |    "credentials": [
       |      {
       |        "host": "repo.example.com",
       |        "user": "value:alice",
       |        "password": "value:$password"
       |      }
       |    ]
       |  },
       |  "httpProxy": {
       |    "password": "value:$password"
       |  }
       |}
       |""".stripMargin

  private def withConfigFile[T](f: os.Path => T): T =
    TestUtil.withTempDir { tmpDir0 =>
      val tmpDir     = os.Path(tmpDir0, os.pwd)
      val configFile = tmpDir / "config.json"
      os.write(configFile, configContent)
      f(configFile)
    }

  private def config(configFile: os.Path, args: String*): String =
    os.proc(launcher, "config", "--config-file", configFile, args).call().out.text()

  /** Runs the config command, expecting it to fail, and returns its stderr */
  private def configError(configFile: os.Path, args: String*): String = {
    val res = os.proc(launcher, "config", "--config-file", configFile, args)
      .call(check = false, stderr = os.Pipe)
    val err = res.err.text()
    assert(res.exitCode == 1)
    // no stack trace
    assert(!err.contains("Exception"))
    assert(!err.contains("\tat "))
    err
  }

  val tests = Tests {
    test("repository credentials") {
      withConfigFile { configFile =>
        val output = config(configFile, "repositories.credentials")
        assert(!output.contains(password))
        assert(output.contains("configRepo0.username=alice"))
        assert(output.contains("configRepo0.password=****"))

        val outputWithPassword = config(configFile, "--password", "repositories.credentials")
        assert(outputWithPassword.contains(s"configRepo0.password=$password"))
      }
    }

    test("password entry") {
      withConfigFile { configFile =>
        val output = config(configFile, "httpProxy.password")
        assert(!output.contains(password))
        assert(output.trim == "value:****")

        val outputWithPassword = config(configFile, "--password", "httpProxy.password")
        assert(outputWithPassword == password)
      }
    }

    test("unknown key") {
      withConfigFile { configFile =>
        val err = configError(configFile, "foo.bar")
        assert(err.linesIterator.length == 1)
        assert(err.startsWith("Error: unrecognized key foo.bar"))
        assert(err.contains("config --help"))
      }
    }

    test("inline credentials") {
      withConfigFile { configFile =>
        val err = configError(
          configFile,
          "repositories.credentials",
          "repo.example.com",
          "value:alice",
          "value:other-secret"
        )
        assert(err.startsWith("Error: Malformed values"))
        assert(err.contains("Inline credentials not accepted"))
        assert(err.contains(s"Config file: $configFile"))
      }
    }

    test("wrong directory permissions") {
      if (Properties.isWin) "disabled"
      else
        TestUtil.withTempDir { tmpDir0 =>
          val dir = os.Path(tmpDir0, os.pwd) / "config-dir"
          os.makeDir(dir)
          os.perms.set(dir, "rwxr-xr-x")
          val configFile = dir / "config.json"
          val err = configError(
            configFile,
            "repositories.mirrors",
            "maven:*=https://repo.example.com/maven"
          )
          assert(err.contains(s"$dir has wrong permissions rwxr-xr-x"))
          assert(err.contains(s"chmod 700 $dir"))
          assert(!os.exists(configFile))
        }
    }

    test("help") {
      val res = os.proc(launcher, "config", "--help").call(stderr = os.Pipe)
      val out = res.out.text()
      // the program name in the usage line is in bold
      assert(out.contains(" [options] [key [value…]]"))
      assert(out.contains("SCALA_CLI_CONFIG"))
      assert(out.contains("--config-file"))
      for (key <- Seq("repositories.mirrors", "repositories.credentials", "httpProxy.address"))
        assert(out.contains(key))
    }
  }
}
