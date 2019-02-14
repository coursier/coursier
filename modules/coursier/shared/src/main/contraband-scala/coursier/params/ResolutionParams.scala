/**
 * This code is generated using [[http://www.scala-sbt.org/contraband/ sbt-contraband]].
 */

// DO NOT EDIT MANUALLY
package coursier.params
final class ResolutionParams private (
  val keepOptionalDependencies: Boolean,
  val maxIterations: Int,
  val forceVersion: Map[coursier.core.Module, String],
  val forcedProperties: Map[String, String],
  val profiles: Set[String],
  val scalaVersion: String,
  val forceScalaVersion: Boolean,
  val typelevel: Boolean) extends Serializable {
  
  private def this() = this(false, 200, Map.empty, Map.empty, Set.empty, coursier.internal.Defaults.scalaVersion, false, false)
  
  override def equals(o: Any): Boolean = o match {
    case x: ResolutionParams => (this.keepOptionalDependencies == x.keepOptionalDependencies) && (this.maxIterations == x.maxIterations) && (this.forceVersion == x.forceVersion) && (this.forcedProperties == x.forcedProperties) && (this.profiles == x.profiles) && (this.scalaVersion == x.scalaVersion) && (this.forceScalaVersion == x.forceScalaVersion) && (this.typelevel == x.typelevel)
    case _ => false
  }
  override def hashCode: Int = {
    37 * (37 * (37 * (37 * (37 * (37 * (37 * (37 * (37 * (17 + "coursier.params.ResolutionParams".##) + keepOptionalDependencies.##) + maxIterations.##) + forceVersion.##) + forcedProperties.##) + profiles.##) + scalaVersion.##) + forceScalaVersion.##) + typelevel.##)
  }
  override def toString: String = {
    "ResolutionParams(" + keepOptionalDependencies + ", " + maxIterations + ", " + forceVersion + ", " + forcedProperties + ", " + profiles + ", " + scalaVersion + ", " + forceScalaVersion + ", " + typelevel + ")"
  }
  private[this] def copy(keepOptionalDependencies: Boolean = keepOptionalDependencies, maxIterations: Int = maxIterations, forceVersion: Map[coursier.core.Module, String] = forceVersion, forcedProperties: Map[String, String] = forcedProperties, profiles: Set[String] = profiles, scalaVersion: String = scalaVersion, forceScalaVersion: Boolean = forceScalaVersion, typelevel: Boolean = typelevel): ResolutionParams = {
    new ResolutionParams(keepOptionalDependencies, maxIterations, forceVersion, forcedProperties, profiles, scalaVersion, forceScalaVersion, typelevel)
  }
  def withKeepOptionalDependencies(keepOptionalDependencies: Boolean): ResolutionParams = {
    copy(keepOptionalDependencies = keepOptionalDependencies)
  }
  def withMaxIterations(maxIterations: Int): ResolutionParams = {
    copy(maxIterations = maxIterations)
  }
  def withForceVersion(forceVersion: Map[coursier.core.Module, String]): ResolutionParams = {
    copy(forceVersion = forceVersion)
  }
  def withForcedProperties(forcedProperties: Map[String, String]): ResolutionParams = {
    copy(forcedProperties = forcedProperties)
  }
  def withProfiles(profiles: Set[String]): ResolutionParams = {
    copy(profiles = profiles)
  }
  def withScalaVersion(scalaVersion: String): ResolutionParams = {
    copy(scalaVersion = scalaVersion)
  }
  def withForceScalaVersion(forceScalaVersion: Boolean): ResolutionParams = {
    copy(forceScalaVersion = forceScalaVersion)
  }
  def withTypelevel(typelevel: Boolean): ResolutionParams = {
    copy(typelevel = typelevel)
  }
}
object ResolutionParams {
  
  def apply(): ResolutionParams = new ResolutionParams()
  def apply(keepOptionalDependencies: Boolean, maxIterations: Int, forceVersion: Map[coursier.core.Module, String], forcedProperties: Map[String, String], profiles: Set[String], scalaVersion: String, forceScalaVersion: Boolean, typelevel: Boolean): ResolutionParams = new ResolutionParams(keepOptionalDependencies, maxIterations, forceVersion, forcedProperties, profiles, scalaVersion, forceScalaVersion, typelevel)
}
