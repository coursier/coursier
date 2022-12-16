package coursier.install

import dataclass.data

import java.util.Locale

@data class GraalvmParams(
  defaultVersion: String,
  extraNativeImageOptions: Seq[String]
)

object GraalvmParams {
  lazy val defaultGraalvmVersion: String = {
    val defaultVersion = "22.3.0"
    if (Platform.isArmArchitecture) s"graalvm-java17:$defaultVersion"
    else s"graalvm-java11:$defaultVersion"
  }

  def resolveGraalVmOptions(graalvmVersion: Option[String]) = {
    val graalvmId = graalvmVersion.map(_.trim).filter(_.nonEmpty).getOrElse(defaultGraalvmVersion)
    val enforceGraalVmJava17 =
      Platform.isArmArchitecture &&
      graalvmId.forall(c => c.isDigit || c == '.' || c == '-')
    val enforceGraalVmJava11 =
      graalvmId.forall(c => c.isDigit || c == '.' || c == '-')

    if (enforceGraalVmJava17) s"graalvm-java17:$graalvmId"
    else if (enforceGraalVmJava11) s"graalvm-java11:$graalvmId"
    else graalvmId
  }
}
