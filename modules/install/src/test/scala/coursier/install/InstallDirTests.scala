package coursier.install

import coursier.cache.{ArtifactError, FileCache}
import coursier.install.internal.PrebuiltApp
import coursier.launcher.Parameters
import coursier.util.Artifact
import utest._

import java.io.FileOutputStream
import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path}
import java.util.Comparator
import java.util.zip.{ZipEntry, ZipOutputStream}

object InstallDirTests extends TestSuite {

  val tests = Tests {
    test("fallback to JVM when pass the GraalVM params") {
      // https://github.com/coursier/coursier/pull/2652

      val mainClass = "main.class"
      val params    = InstallDir().params(
        AppDescriptor().copy(launcherType = LauncherType.GraalvmNativeImage),
        AppArtifacts(),
        Nil,
        mainClass
      )

      val bootstrapParams = params match {
        case b: Parameters.Bootstrap => b
        case _                       => sys.error(s"Unrecognized parameters type: $params")
      }

      assert(bootstrapParams.mainClass == mainClass)
    }

    test("assume SSL handshake exceptions are not found errors") {
      PrebuiltApp.handleArtifactErrors(
        Left(new ArtifactError.DownloadError(
          "foo",
          Some(new javax.net.ssl.SSLHandshakeException("foo"))
        )),
        Artifact(
          "https://repo1.maven.org/maven2/org/scala-lang/scala-library/7.12.14/scala-library-7.12.14.jar"
        ),
        verbosity = 0
      )
    }

    test("list should return the list of installed apps and skip directories") {
      def createApp(dir: Path, name: String): Unit = {
        val app = dir.resolve(name)
        val out = new ZipOutputStream(new FileOutputStream(app.toFile))
        try {
          val entry = new ZipEntry("META-INF/coursier/info.json")
          out.putNextEntry(entry)
        }
        finally
          out.close()
      }

      val tempDir = Files.createTempDirectory("installDirTests")
      try {
        val dir = tempDir.resolve("directory-should-be-ignored")
        Files.createDirectories(dir)

        createApp(tempDir, ".dot-app-should-be-ignored")
        createApp(tempDir, "app1")

        val installedApps = InstallDir(tempDir).list()
        assert(installedApps == Seq("app1"))
      }
      finally
        Files.walk(tempDir).sorted(Comparator.reverseOrder()).forEach(Files.delete(_))
    }

    test("versionOf") {

      def descriptor(json: String): AppDescriptor =
        InfoFile.appDescriptor("test-descriptor", json.getBytes(StandardCharsets.UTF_8)) match {
          case Left(ex)    => throw ex
          case Right(desc) => desc
        }

      def lock(urls: String*): ArtifactsLock =
        ArtifactsLock(
          urls
            .zipWithIndex
            .map {
              case (url, idx) =>
                ArtifactsLock.Entry(url, "SHA-1", f"$idx%040d")
            }
            .toSet
        )

      test("main dependency of a scala app") {
        // the main dependency doesn't come first alphabetically here, on purpose
        val desc = descriptor(
          """{"dependencies": ["org.scalameta::scalafmt-cli:latest.stable"]}"""
        )
        val lock0 = lock(
          "https://repo1.maven.org/maven2/com/geirsson/metaconfig-core_2.13/0.11.1/metaconfig-core_2.13-0.11.1.jar",
          "https://repo1.maven.org/maven2/org/scala-lang/scala-library/2.13.14/scala-library-2.13.14.jar",
          "https://repo1.maven.org/maven2/org/scalameta/scalafmt-cli_2.13/3.9.6/scalafmt-cli_2.13-3.9.6.jar"
        )
        assert(InstallDir.versionOf(desc, lock0) == Some("3.9.6"))
      }

      test("main dependency of a java app") {
        val desc = descriptor(
          """{"dependencies": ["org.virtuslab.scala-cli:cli_3:latest.release"]}"""
        )
        val lock0 = lock(
          "https://repo1.maven.org/maven2/org/virtuslab/scala-cli/cli_3/1.16.0/cli_3-1.16.0.jar",
          "https://repo1.maven.org/maven2/org/virtuslab/scala-cli/config_3/1.0.4/config_3-1.0.4.jar"
        )
        assert(InstallDir.versionOf(desc, lock0) == Some("1.16.0"))
      }

      test("prebuilt launcher") {
        val desc = descriptor(
          """{
            |  "dependencies": ["io.get-coursier::coursier-cli:latest.release"],
            |  "launcherType": "graalvm-native-image",
            |  "prebuiltBinaries": {
            |    "x86_64-pc-linux": "gz+https://github.com/coursier/coursier/releases/download/v${version}/cs-x86_64-pc-linux.gz"
            |  }
            |}""".stripMargin
        )
        val lock0 = lock(
          "https://github.com/coursier/coursier/releases/download/v2.1.25-M26/cs-x86_64-pc-linux.gz"
        )
        // the version, not the "v2.1.25-M26" tag the URL is built from
        assert(InstallDir.versionOf(desc, lock0) == Some("2.1.25-M26"))
      }

      test("prebuilt launcher from a version override") {
        val desc = descriptor(
          """{
            |  "dependencies": ["io.get-coursier::coursier-cli:latest.release"],
            |  "launcherType": "graalvm-native-image",
            |  "prebuiltBinaries": {
            |    "aarch64-pc-linux": "gz+https://github.com/coursier/coursier/releases/download/v${version}/cs-aarch64-pc-linux.gz"
            |  },
            |  "versionOverrides": [
            |    {
            |      "versionRange": "(2.1.0-RC4, 2.1.15]",
            |      "prebuiltBinaries": {
            |        "aarch64-pc-linux": "gz+https://github.com/VirtusLab/coursier-m1/releases/download/v${version}/cs-aarch64-pc-linux.gz"
            |      }
            |    }
            |  ]
            |}""".stripMargin
        )
        val lock0 = lock(
          "https://github.com/VirtusLab/coursier-m1/releases/download/v2.1.10/cs-aarch64-pc-linux.gz"
        )
        assert(InstallDir.versionOf(desc, lock0) == Some("2.1.10"))
      }

      test("no version rather than a wrong one") {
        val desc = descriptor(
          """{"dependencies": ["org.scalameta::scalafmt-cli:latest.stable"]}"""
        )
        // ivy-like layout, that we can't read a version off
        val lock0 = lock(
          "https://foo.com/org.scalameta/scalafmt-cli_2.13/3.9.6/jars/scalafmt-cli_2.13.jar"
        )
        assert(InstallDir.versionOf(desc, lock0) == None)
      }
    }

    test("listWithVersions") {

      def createApp(
        dir: Path,
        name: String,
        descriptor: String,
        lock: Option[String]
      ): Unit = {
        val app = dir.resolve(name)
        val out = new ZipOutputStream(new FileOutputStream(app.toFile))
        try {
          out.putNextEntry(new ZipEntry("META-INF/coursier/info.json"))
          out.write(descriptor.getBytes(StandardCharsets.UTF_8))
          out.closeEntry()
          for (lock0 <- lock) {
            out.putNextEntry(new ZipEntry("META-INF/coursier/lock-file"))
            out.write(lock0.getBytes(StandardCharsets.UTF_8))
            out.closeEntry()
          }
        }
        finally
          out.close()
      }

      val tempDir = Files.createTempDirectory("installDirTests")
      try {
        createApp(
          tempDir,
          "scalafmt",
          """{"dependencies": ["org.scalameta::scalafmt-cli:latest.stable"]}""",
          Some(
            "https://repo1.maven.org/maven2/org/scalameta/scalafmt-cli_2.13/3.9.6/scalafmt-cli_2.13-3.9.6.jar#SHA-1:0000000000000000000000000000000000000000"
          )
        )
        // no lock file, e.g. a launcher written by an older coursier
        createApp(
          tempDir,
          "no-lock",
          """{"dependencies": ["org.scalameta::scalafmt-cli:latest.stable"]}""",
          None
        )

        val installedApps = InstallDir(tempDir).listWithVersions()
        assert(installedApps == Seq("no-lock" -> None, "scalafmt" -> Some("3.9.6")))
        assert(InstallDir(tempDir).list() == installedApps.map(_._1))
      }
      finally
        Files.walk(tempDir).sorted(Comparator.reverseOrder()).forEach(Files.delete(_))
    }
  }

}
