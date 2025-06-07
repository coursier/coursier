package coursier.install

import coursier.cache.{ArtifactError, FileCache}
import coursier.install.internal.PrebuiltApp
import coursier.launcher.Parameters
import coursier.util.Artifact
import utest._

import java.io.FileOutputStream
import java.nio.file.{Files, Path}
import java.util.Comparator
import java.util.zip.{ZipEntry, ZipOutputStream}

object InstallDirTests extends TestSuite {

  val tests = Tests {
    test("fallback to JVM when pass the GraalVM params") {
      // https://github.com/coursier/coursier/pull/2652

      val mainClass = "main.class"
      val params    = InstallDir().params(
        AppDescriptor().withLauncherType(LauncherType.GraalvmNativeImage),
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

        val installedApps = new InstallDir(tempDir, FileCache()).list()
        assert(installedApps == Seq("app1"))
      }
      finally
        Files.walk(tempDir).sorted(Comparator.reverseOrder()).forEach(Files.delete(_))
    }
  }

}
