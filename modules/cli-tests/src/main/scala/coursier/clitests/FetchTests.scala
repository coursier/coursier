package coursier.clitests

import utest._

abstract class FetchTests extends TestSuite {

  def launcher: String

  val tests = Tests {
    test("mirror") {
      TestUtil.withTempDir { tmpDir0 =>
        val tmpDir = os.Path(tmpDir0, os.pwd)
        val cache  = tmpDir / "cache"

        val res0 =
          os.proc(launcher, "fetch", "org.scala-lang:scala-compiler:2.13.8", "--cache", cache)
            .call()
        val jars0 = res0.out.text()
          .linesIterator
          .toVector
          .map(os.Path(_).relativeTo(cache))

        val centralPrefix = os.rel / "https" / "repo1.maven.org" / "maven2"
        assert(jars0.forall(_.startsWith(centralPrefix)))

        val configContent =
          s"""{
             |  "repositories": {
             |    "mirrors": [
             |      "https://maven-central.storage-download.googleapis.com/maven2 = https://repo1.maven.org/maven2"
             |    ]
             |  }
             |}
             |""".stripMargin
        val configFile = tmpDir / "config.json"
        os.write(configFile, configContent)

        val res1 =
          os.proc(launcher, "fetch", "org.scala-lang:scala-compiler:2.13.8", "--cache", cache)
            .call(env = Map("SCALA_CLI_CONFIG" -> configFile.toString))
        val jars1 = res1.out.text()
          .linesIterator
          .toVector
          .map(os.Path(_).relativeTo(cache))

        val gcsPrefix =
          os.rel / "https" / "maven-central.storage-download.googleapis.com" / "maven2"
        assert(jars1.forall(_.startsWith(gcsPrefix)))
      }
    }

    test("default repositories") {
      TestUtil.withTempDir { tmpDir0 =>
        val tmpDir = os.Path(tmpDir0, os.pwd)
        val cache  = tmpDir / "cache"

        val res0 =
          os.proc(launcher, "fetch", "org.scala-lang:scala-compiler:2.13.8", "--cache", cache)
            .call()
        val jars0 = res0.out.text()
          .linesIterator
          .toVector
          .map(os.Path(_).relativeTo(cache))

        val centralPrefix = os.rel / "https" / "repo1.maven.org" / "maven2"
        assert(jars0.forall(_.startsWith(centralPrefix)))

        val configContent =
          s"""{
             |  "repositories": {
             |    "default": [
             |      "https://maven-central.storage-download.googleapis.com/maven2"
             |    ]
             |  }
             |}
             |""".stripMargin
        val configFile = tmpDir / "config.json"
        os.write(configFile, configContent)

        val res1 =
          os.proc(launcher, "fetch", "org.scala-lang:scala-compiler:2.13.8", "--cache", cache)
            .call(env = Map("SCALA_CLI_CONFIG" -> configFile.toString))
        val jars1 = res1.out.text()
          .linesIterator
          .toVector
          .map(os.Path(_).relativeTo(cache))

        val gcsPrefix =
          os.rel / "https" / "maven-central.storage-download.googleapis.com" / "maven2"
        assert(jars1.forall(_.startsWith(gcsPrefix)))
      }
    }
  }
}
