package coursier.install

import java.nio.file.Paths
import coursier.launcher.Parameters
import utest._

object InstallDirTests extends TestSuite {

  val tests = Tests {
    test("pass the GraalVM version to launcher params") {

      val version = "X.Y.Z"

      val installDir = InstallDir()
        .withGraalvmParamsOpt(Some(GraalvmParams(Some(version), Nil)))

      val params = installDir.params(
        AppDescriptor().withLauncherType(LauncherType.GraalvmNativeImage),
        AppArtifacts(),
        Nil,
        "main.class",
        Paths.get("/foo")
      )

      val nativeParams = params match {
        case n: Parameters.NativeImage => n
        case _ => sys.error(s"Unrecognized parameters type: $params")
      }

      assert(nativeParams.graalvmVersion == Some(version))
    }
  }

}
