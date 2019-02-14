package coursier.params

abstract class ResolutionParamsHelpers {
  def forceScalaVersion: Option[Boolean]
  def scalaVersion: Option[String]

  def doForceScalaVersion: Boolean =
    forceScalaVersion.getOrElse {
      scalaVersion.nonEmpty
    }
  def selectedScalaVersion: String =
    scalaVersion.getOrElse {
      coursier.internal.Defaults.scalaVersion
    }
}
