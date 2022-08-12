package coursier.clitests

import java.io.File
import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Paths}

import coursier.clitests.util.TestAuthProxy
import utest._
import scala.util.Properties

abstract class GetTests extends TestSuite {

  def launcher: String

  def hasDocker: Boolean =
    Properties.isLinux

  private def pomAscUrl =
    "https://repo1.maven.org/maven2/io/get-coursier/coursier_2.13/2.0.2/coursier_2.13-2.0.2.pom.asc"
  private def pomContent =
    """-----BEGIN PGP SIGNATURE-----
      |
      |iQEcBAABAgAGBQJfgK9TAAoJEM0bnY3ITspWmQgIAKlD5P5OsIfJs/O3d5ZtaaI6
      |lrkdLZMS5i1eus7+qH7JMC8ZjaJ0/vM0AoXXLNFevjb51Xm1mmqMyyZVEKYm1RuE
      |f7HQCOAmB0eqLVVTLRv3wYatMRxXYmBqkQpvUdRestYqiaK6eTTUPys3RDfgVjgr
      |8vP1rkpWJ2c6IfVBmb1UhEMK4UFTryn0mVZ7dfBol8cb/+Z2rpksAygvzl1zTgvH
      |0WR7ttpB93D+quQwo5iwqv0Bz3LPV/HD2CFY9C4lz0EsLFwYxvQtub3we4MEy6u+
      |4HugYf3fFUZod+22Im0uh15ETvyqVqZFUXFPmTXN/P3QGcSIyJNitqI0DOrtTOw=
      |=geJ4
      |-----END PGP SIGNATURE-----
      |""".stripMargin.replace("\r\n", "\n")

  val tests = Tests {
    test("Maven Central") {
      TestUtil.withTempDir { tmpDir =>
        val cache = new File(tmpDir, "cache").getAbsolutePath
        val output =
          os.proc(
            launcher,
            "get",
            pomAscUrl,
            "--cache",
            cache
          )
            .call()
            .out.text()
        val content = new String(Files.readAllBytes(Paths.get(output.trim)), StandardCharsets.UTF_8)
        assert(content == pomContent)
      }
    }

    test("GitHub") {
      TestUtil.withTempDir { tmpDir =>
        val cache = new File(tmpDir, "cache").getAbsolutePath
        val output =
          os.proc(
            launcher,
            "get",
            "https://github.com/coursier/coursier/releases/download/v2.0.2/coursier.asc",
            "--cache",
            cache
          )
            .call()
            .out.text()
        val content = new String(Files.readAllBytes(Paths.get(output.trim)), StandardCharsets.UTF_8)
        val expectedContent =
          """-----BEGIN PGP SIGNATURE-----
            |
            |iQEcBAABAgAGBQJfgLkhAAoJEM0bnY3ITspWwG4IAK4DNWn7nrGZGjf28e/r0Emi
            |qMK0tA+jhtIEavEDpkQjmTD+hYKgYj2ixFPvNZrWqJ6JQAr2fJDAVQpe6HnP5eZe
            |emMAx7wDZNE9VVVhDqqi3zQ25zcZJuD99eMJz690pE3eg7aflnAK9PhyJvpwZnx2
            |TO8PuSqEy1dq2Ixa0jCUIWKX4deFEcjy60+kOtcNdBXHh2l/PTaDWtFi/EG3tfTv
            |tHIic5ZkEDRBYBo8NOkBAvpuE4AiUj7/6/gJiwExAaA+0EnzRKERot7Y03ZB1Up7
            |+czyB/AK6N1R7HGG7uVeUgG4O4TYqx501aU2zYjIFwMoclk+4NhFEAEcBby8DgE=
            |=8g4q
            |-----END PGP SIGNATURE-----
            |""".stripMargin.replace("\r\n", "\n")
        assert(content == expectedContent)
      }
    }

    test("Maven Central via authenticated proxy") {
      if (hasDocker) mavenCentralViaAuthProxyTest()
      else "Docker test disabled"
    }
    def mavenCentralViaAuthProxyTest(): Unit =
      TestUtil.withTempDir { tmpDir0 =>
        val tmpDir = os.Path(tmpDir0, os.pwd)
        val cache  = (tmpDir / "cache").toString

        val okM2Dir = tmpDir / "m2-ok"
        os.write(okM2Dir / "settings.xml", TestAuthProxy.m2Settings(), createFolders = true)
        val nopeM2Dir = tmpDir / "m2-nope"
        os.write(
          nopeM2Dir / "settings.xml",
          TestAuthProxy.m2Settings(9083, "wrong", "nope"),
          createFolders = true
        )

        val output = TestAuthProxy.withAuthProxy { _ =>

          val proc = os.proc(launcher, "get", pomAscUrl)

          val failCheck = proc
            .call(
              cwd = tmpDir,
              mergeErrIntoOut = true,
              check = false,
              env = Map(
                "COURSIER_CACHE" -> (tmpDir / "cache-1").toString,
                "CS_MAVEN_HOME"  -> nopeM2Dir.toString
              )
            )

          assert(failCheck.exitCode != 0)
          assert(failCheck.out.text().contains("407 Proxy Authentication Required"))

          proc
            .call(
              cwd = tmpDir,
              env = Map(
                "COURSIER_CACHE" -> (tmpDir / "cache-2").toString,
                "CS_MAVEN_HOME"  -> okM2Dir.toString
              )
            )
            .out.text()
        }
        val content = new String(Files.readAllBytes(Paths.get(output.trim)), StandardCharsets.UTF_8)
        assert(content == pomContent)
      }

    def tarArchiveTest(ext: String): Unit = {
      val resourcePath = s"archives/archive.tar$ext"
      val archiveUrl   = Thread.currentThread().getContextClassLoader.getResource(resourcePath)
      Predef.assert(archiveUrl != null, s"Resource $resourcePath not found")
      assert(archiveUrl.getProtocol == "file")

      TestUtil.withTempDir { tmpDir =>
        val cache = new File(tmpDir, "cache").getAbsolutePath
        val output =
          os.proc(
            launcher,
            "get",
            archiveUrl.toString,
            "--cache",
            cache,
            "--archive"
          )
            .call()
            .out.text()
        val dir = Paths.get(output.trim)
        val content =
          new String(Files.readAllBytes(dir.resolve("archive/a")), StandardCharsets.UTF_8)
        val expectedContent = "a\n"
        assert(content == expectedContent)
      }
    }

    test("tgz archive") {
      tarArchiveTest(".gz")
    }
    test("tbz2 archive") {
      tarArchiveTest(".bz2")
    }
  }

}
