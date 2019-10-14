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
  object Dependency {
    def apply(
      module: Module,
      version: String
    ): Dependency =
      core.Dependency(module, version)
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

  type ProjectCache = Map[ModuleVersion, (ArtifactSource, Project)]

  type Repository = core.Repository
  val Repository = core.Repository

  type MavenRepository = maven.MavenRepository
  val MavenRepository = maven.MavenRepository

  type Resolution = core.Resolution
  // ought to be replaced by just  val Resolution = core.Resolution
  object Resolution {
    val empty = apply()
    def apply(): Resolution =
      core.Resolution()
    def apply(dependencies: Seq[Dependency]): Resolution =
      core.Resolution().withRootDependencies(dependencies)

    def defaultTypes: Set[Type] = coursier.core.Resolution.defaultTypes
  }

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
