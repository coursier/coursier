package coursier.tests

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path, Paths}

import coursier.CoursierEnv
import coursier.core.Authentication
import coursier.params.MavenSettingsMirror
import coursier.util.EnvValues
import utest._

object MavenSettingsFileTests extends TestSuite {

  private val noValues = EnvValues(None, None)

  private def withTmpDir[T](f: Path => T): T = {
    val tmpDir = Files.createTempDirectory("coursier-maven-settings-tests")
    try f(tmpDir)
    finally
      Files.walk(tmpDir)
        .sorted(java.util.Comparator.reverseOrder[Path])
        .forEach(Files.deleteIfExists(_))
  }

  private def writeSettings(dir: Path, content: String): Path = {
    val settingsFile = dir.resolve("settings.xml")
    Files.write(settingsFile, content.getBytes(StandardCharsets.UTF_8))
    settingsFile
  }

  val tests = Tests {
    test("mirrorsFromFile") {
      withTmpDir { tmpDir =>
        val settingsFile = writeSettings(
          tmpDir,
          """<settings>
            |  <servers>
            |    <server>
            |      <id>internal</id>
            |      <username>alex</username>
            |      <password>1234</password>
            |    </server>
            |  </servers>
            |  <mirrors>
            |    <mirror>
            |      <id>internal</id>
            |      <url>https://nexus.example.com/repository/maven-public</url>
            |      <mirrorOf>*</mirrorOf>
            |    </mirror>
            |  </mirrors>
            |</settings>
            |""".stripMargin
        )

        val expected = Seq(
          MavenSettingsMirror(
            "*",
            "https://nexus.example.com/repository/maven-public",
            Some(Authentication("alex", "1234"))
          )
        )

        val mirrors = CoursierEnv.mavenSettingsMirrors(settingsFile)

        assert(mirrors == expected)
      }
    }

    test("missingFile") {
      withTmpDir { tmpDir =>
        val mirrors = CoursierEnv.mavenSettingsMirrors(tmpDir.resolve("settings.xml"))
        assert(mirrors.isEmpty)
      }
    }

    test("malformedFile") {
      withTmpDir { tmpDir =>
        val settingsFile = writeSettings(tmpDir, "<settings>")
        val exOpt =
          try {
            CoursierEnv.mavenSettingsMirrors(settingsFile)
            None
          }
          catch {
            case e: Exception => Some(e)
          }
        assert(exOpt.exists(_.getMessage.startsWith(s"Error parsing $settingsFile")))
        // the default mirrors just ignore settings files that cannot be parsed
        val mirrors = CoursierEnv.defaultMavenSettingsMirrors(
          EnvValues(Some(settingsFile.toString), None),
          noValues,
          noValues
        )
        assert(mirrors.isEmpty)
      }
    }

    test("defaultSettingsFile") {
      test("disabled") {
        val fileOpt =
          CoursierEnv.defaultMavenSettingsFile(EnvValues(Some("false"), None), noValues, noValues)
        assert(fileOpt.isEmpty)
      }

      test("explicitPath") {
        val fileOpt = CoursierEnv.defaultMavenSettingsFile(
          EnvValues(Some("/etc/maven/settings.xml"), None),
          EnvValues(Some("/opt/maven-home"), None),
          noValues
        )
        assert(fileOpt == Some(Paths.get("/etc/maven/settings.xml")))
      }

      test("mavenHome") {
        withTmpDir { tmpDir =>
          val settingsFile = writeSettings(tmpDir, "<settings/>")
          val fileOpt = CoursierEnv.defaultMavenSettingsFile(
            EnvValues(Some("true"), None),
            EnvValues(Some(tmpDir.toString), None),
            EnvValues(Some("/opt/other-maven-home"), None)
          )
          assert(fileOpt == Some(settingsFile))
        }
      }

      test("mavenHomeFallback") {
        withTmpDir { tmpDir =>
          val settingsFile = writeSettings(tmpDir, "<settings/>")
          val fileOpt = CoursierEnv.defaultMavenSettingsFile(
            noValues,
            EnvValues(Some("/opt/maven-home"), None),
            EnvValues(None, Some(tmpDir.toString))
          )
          assert(fileOpt == Some(settingsFile))
        }
      }

      test("ignoresMavenHomesWithNoSettingsFile") {
        // MAVEN_HOME usually points at a Maven installation, which has no settings.xml at its root
        val fileOpt = CoursierEnv.defaultMavenSettingsFile(
          noValues,
          noValues,
          EnvValues(Some("/opt/other-maven-home"), None)
        )
        val expected = Option(System.getProperty("user.home"))
          .map(Paths.get(_).resolve(".m2").resolve("settings.xml"))
        assert(fileOpt == expected)
      }

      test("userHome") {
        val fileOpt = CoursierEnv.defaultMavenSettingsFile(noValues, noValues, noValues)
        val expected = Option(System.getProperty("user.home"))
          .map(Paths.get(_).resolve(".m2").resolve("settings.xml"))
        assert(fileOpt == expected)
      }
    }
  }
}
