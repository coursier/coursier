package coursier.clitests

import utest._

abstract class FetchTests extends TestSuite {

  def launcher: String

  val tests = Tests {
    val `/` = java.io.File.separator

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

    test("Scala 3 partial version") {
      val res0 =
        os.proc(launcher, "fetch", "org.scalacheck::scalacheck:1.16.0", "--scala-version", "3")
          .call()
      assert(res0.exitCode == 0)
      val output = res0.out.text()
      val scalacheckPath = Seq("org", "scalacheck", "scalacheck_3", "1.16.0").mkString(`/`)
      val scalaLibraryPath = Seq("org", "scala-lang", "scala3-library_3").mkString(`/`)
      assert(output.contains(scalacheckPath) && output.contains(scalaLibraryPath))
    }

    test("Scala 3 partial version with two numbers") {
      val res0 =
        os.proc(launcher, "fetch", "org.scalacheck::scalacheck:1.16.0", "--scala-version", "3.2")
          .call()
      assert(res0.exitCode == 0)
      val output = res0.out.text()
      val scalacheckPath = Seq("org", "scalacheck", "scalacheck_3", "1.16.0").mkString(`/`)
      val scalaLibraryPath = Seq("org", "scala-lang", "scala3-library_3", "3.2").mkString(`/`)
      assert(output.contains(scalacheckPath) && output.contains(scalaLibraryPath))
    }

    test("Scala 2 partial version with one number") {
      val res0 =
        os.proc(launcher, "fetch", "org.scalacheck::scalacheck:1.16.0", "--scala-version", "2")
          .call()
      assert(res0.exitCode == 0)
      val output = res0.out.text()
      val scalacheckPath = Seq("org", "scalacheck", "scalacheck_2.13", "1.16.0").mkString(`/`)
      val scalaLibraryPath = Seq("org", "scala-lang", "scala-library", "2.13.").mkString(`/`)
      assert(output.contains(scalacheckPath) && output.contains(scalaLibraryPath))
    }
  }
}
