import coursier.core._
import coursier.util.StringInterpolators._

import scala.language.implicitConversions

/**
 * Mainly pulls definitions from coursier.core, sometimes with default arguments.
 */
package object coursier {

  // `extends Serializable` added here-or-there for bin compat while switching from 2.12.1 to 2.12.4

  type Organization = core.Organization
  val Organization = core.Organization

  type ModuleName = core.ModuleName
  val ModuleName = core.ModuleName

  type Dependency = core.Dependency
  object Dependency extends Serializable {
    def apply(
      module: Module,
      version: String,
      configuration: Configuration = Configuration.empty,
      attributes: Attributes = Attributes(),
      exclusions: Set[(Organization, ModuleName)] = Set.empty,
      optional: Boolean = false,
      transitive: Boolean = true
    ): Dependency =
      core.Dependency(
        module,
        version,
        configuration,
        exclusions,
        attributes,
        optional,
        transitive
      )
  }

  type Attributes = core.Attributes
  object Attributes extends Serializable {
    def apply(
      `type`: Type = Type.empty,
      classifier: Classifier = Classifier.empty
    ): Attributes =
      core.Attributes(`type`, classifier)
  }

  type Project = core.Project
  val Project = core.Project

  type Info = core.Info
  val Info = core.Info

  type Profile = core.Profile
  val Profile = core.Profile

  type Module = core.Module
  object Module extends Serializable {
    def apply(organization: Organization, name: ModuleName, attributes: Map[String, String] = Map.empty): Module =
      core.Module(organization, name, attributes)
  }

  type ModuleVersion = (core.Module, String)

  type ProjectCache = Map[ModuleVersion, (Artifact.Source, Project)]

  type Repository = core.Repository
  val Repository = core.Repository

  type MavenRepository = maven.MavenRepository
  val MavenRepository = maven.MavenRepository

  type Resolution = core.Resolution
  object Resolution extends Serializable {
    val empty = apply()
    def apply(
      rootDependencies: Seq[Dependency] = Nil,
      dependencies: Set[Dependency] = Set.empty,
      forceVersions: Map[Module, String] = Map.empty,
      conflicts: Set[Dependency] = Set.empty,
      projectCache: ProjectCache = Map.empty,
      errorCache: Map[ModuleVersion, Seq[String]] = Map.empty,
      finalDependencies: Map[Dependency, Seq[Dependency]] = Map.empty,
      filter: Option[Dependency => Boolean] = None,
      osInfo: Activation.Os = Activation.Os.fromProperties(sys.props.toMap),
      jdkVersion: Option[Version] = sys.props.get("java.version").flatMap(Parse.version),
      userActivations: Option[Map[String, Boolean]] = None,
      mapDependencies: Option[Dependency => Dependency] = None,
      extraProperties: Seq[(String, String)] = Nil,
      forceProperties: Map[String, String] = Map.empty
    ): Resolution =
      core.Resolution(
        rootDependencies,
        dependencies,
        forceVersions,
        conflicts,
        projectCache,
        errorCache,
        finalDependencies,
        filter,
        osInfo,
        jdkVersion,
        userActivations,
        mapDependencies,
        extraProperties,
        forceProperties
      )
  }

  type Artifact = core.Artifact
  val Artifact = core.Artifact

  type Classifier = core.Classifier
  val Classifier = core.Classifier

  type Type = core.Type
  val Type = core.Type

  type ResolutionProcess = core.ResolutionProcess
  val ResolutionProcess = core.ResolutionProcess

  implicit class ResolutionExtensions(val underlying: Resolution) extends AnyVal {

    def process: ResolutionProcess = ResolutionProcess(underlying)
  }

  implicit def organizationString(sc: StringContext): SafeOrganization =
    SafeOrganization(sc)
  implicit def moduleNameString(sc: StringContext): SafeModuleName =
    SafeModuleName(sc)
  implicit def moduleString(sc: StringContext): SafeModule =
    SafeModule(sc)
  implicit def moduleExclString(sc: StringContext): SafeModuleExclusionMatcher =
    SafeModuleExclusionMatcher(sc)
  implicit def moduleInclString(sc: StringContext): SafeModuleInclusionMatcher =
    SafeModuleInclusionMatcher(sc)
  implicit def dependencyString(sc: StringContext): SafeDependency =
    SafeDependency(sc)
  implicit def mavenRepositoryString(sc: StringContext): SafeMavenRepository =
    SafeMavenRepository(sc)
  implicit def ivyRepositoryString(sc: StringContext): SafeIvyRepository =
    SafeIvyRepository(sc)

}
