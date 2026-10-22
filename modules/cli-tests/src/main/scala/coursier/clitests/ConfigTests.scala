package coursier.clitests

import utest._

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
  }
}
