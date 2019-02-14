package coursier.params

abstract class PlatformResolutionParamsCompanion {
  def defaultScalaVersion: String =
    "2.12.8" // FIXME Get from sbt or scalac
}
