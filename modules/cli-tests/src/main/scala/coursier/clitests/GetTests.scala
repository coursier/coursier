package coursier.clitests

import java.io.File
import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Paths}

import utest._

abstract class GetTests extends TestSuite {

  def launcher: String

  val tests = Tests {
    test("Maven Central") {
      TestUtil.withTempDir { tmpDir =>
        val cache = new File(tmpDir, "cache").getAbsolutePath
        val output = LauncherTestUtil.output(
          launcher,
          "get",
          "https://repo1.maven.org/maven2/io/get-coursier/coursier_2.13/2.0.2/coursier_2.13-2.0.2.pom.asc",
          "--cache",
          cache
        )
        val content = new String(Files.readAllBytes(Paths.get(output.trim)), StandardCharsets.UTF_8)
        val expectedContent =
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
        assert(content == expectedContent)
      }
    }

    test("GitHub") {
      TestUtil.withTempDir { tmpDir =>
        val cache = new File(tmpDir, "cache").getAbsolutePath
        val output = LauncherTestUtil.output(
          launcher,
          "get",
          "https://github.com/coursier/coursier/releases/download/v2.0.2/coursier.asc",
          "--cache",
          cache
        )
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

    def tarArchiveTest(ext: String): Unit = {
      val resourcePath = s"archives/archive.tar$ext"
      val archiveUrl   = Thread.currentThread().getContextClassLoader.getResource(resourcePath)
      Predef.assert(archiveUrl != null, s"Resource $resourcePath not found")
      assert(archiveUrl.getProtocol == "file")

      TestUtil.withTempDir { tmpDir =>
        val cache = new File(tmpDir, "cache").getAbsolutePath
        val output = LauncherTestUtil.output(
          launcher,
          "get",
          archiveUrl.toString,
          "--cache",
          cache,
          "--archive"
        )
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
