package coursier.install

import org.scalatest.BeforeAndAfterAll
import org.scalatest.flatspec.AnyFlatSpec
import java.nio.file.Paths
import coursier.launcher.Parameters

class InstallDirTests extends AnyFlatSpec with BeforeAndAfterAll {

  it should "pass the GraalVM version to launcher params" in {

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
