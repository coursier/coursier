package coursier.params

abstract class PlatformResolutionParamsCompanion {
  def defaultScalaVersion: String =
    scala.util.Properties.versionNumberString
}
