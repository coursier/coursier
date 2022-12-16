package coursier.install

import java.nio.file.Paths

import coursier.cache.ArtifactError
import coursier.install.internal.PrebuiltApp
import coursier.launcher.Parameters
import coursier.util.Artifact

import utest._

object InstallDirTests extends TestSuite {

  val tests = Tests {
    test("pass the GraalVM version to launcher params") {

      val version = "X.Y.Z"

      val installDir = InstallDir()
        .withGraalvmParamsOpt(Some(GraalvmParams(version, Nil)))

      val params = installDir.params(
        AppDescriptor().withLauncherType(LauncherType.GraalvmNativeImage),
        AppArtifacts(),
        Nil,
        "main.class",
        Paths.get("/foo")
      )

      val nativeParams = params match {
        case n: Parameters.NativeImage => n
        case _                         => sys.error(s"Unrecognized parameters type: $params")
      }

      assert(nativeParams.graalvmVersion == Some(version))
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
  }

}
