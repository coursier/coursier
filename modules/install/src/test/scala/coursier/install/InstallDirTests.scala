package coursier.install

import java.nio.file.Paths

import coursier.cache.ArtifactError
import coursier.install.internal.PrebuiltApp
import coursier.launcher.Parameters
import coursier.util.Artifact

import utest._

object InstallDirTests extends TestSuite {

  val tests = Tests {
    test("fallback to JVM when pass the GraalVM params") { // https://github.com/coursier/coursier/pull/2652

      val mainClass = "main.class"
      val params = InstallDir().params(
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
  }

}
