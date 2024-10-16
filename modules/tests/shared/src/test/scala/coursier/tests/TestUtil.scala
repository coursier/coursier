package coursier.tests

import coursier.core.{Configuration, Dependency, Info, Module, Profile, Resolution, Type}

import scala.collection.compat._

object TestUtil {

  implicit class DependencyOps(val underlying: Dependency) extends AnyVal {
    def withCompileScope: Dependency = underlying.withConfiguration(Configuration.defaultCompile)
  }

  private val projectProperties = Set(
    "pom.groupId",
    "pom.artifactId",
    "pom.version",
    "groupId",
    "artifactId",
    "version",
    "project.groupId",
    "project.artifactId",
    "project.version",
    "project.packaging",
    "project.parent.groupId",
    "project.parent.artifactId",
    "project.parent.version",
    "parent.groupId",
    "parent.artifactId",
    "parent.version"
  )

  implicit class ResolutionOps(val underlying: Resolution) extends AnyVal {

    // The content of these fields is typically not validated in the tests.
    // It can be cleared with these method to it easier to compare `underlying`
    // to an expected value.

    def clearFinalDependenciesCache: Resolution =
      underlying.withFinalDependenciesCache(Map.empty)
    def clearCaches: Resolution =
      underlying
        .withProjectCache(Map.empty)
        .withErrorCache(Map.empty)
        .withFinalDependenciesCache(Map.empty)
    def clearDependencyOverrides: Resolution =
      underlying.withDependencies(
        underlying.dependencies.map(_.withOverrides(Map.empty))
      )
    def clearFilter: Resolution =
      underlying.withFilter(None)
    def clearProjectProperties: Resolution =
      underlying.withProjectCache(
        underlying
          .projectCache
          .view
          .mapValues {
            case (s, p) =>
              (s, p.withProperties(p.properties.filter { case (k, _) => !projectProperties(k) }))
          }
          .iterator
          .toMap
      )
  }

  object Profile {
    type Activation = coursier.core.Activation
    object Activation {
      def apply(properties: Seq[(String, Option[String])] = Nil): Activation =
        coursier.core.Activation(properties, coursier.core.Activation.Os.empty, None)
    }

    def apply(
      id: String,
      activeByDefault: Option[Boolean] = None,
      activation: Activation = Activation(),
      dependencies: Seq[(Configuration, Dependency)] = Nil,
      dependencyManagement: Seq[(Configuration, Dependency)] = Nil,
      properties: Map[String, String] = Map.empty
    ) =
      coursier.core.Profile(
        id,
        activeByDefault,
        activation,
        dependencies,
        dependencyManagement,
        properties
      )
  }

  type Project = coursier.core.Project
  object Project {
    def apply(
      module: Module,
      version: String,
      dependencies: Seq[(Configuration, Dependency)] = Seq.empty,
      parent: Option[(Module, String)] = None,
      dependencyManagement: Seq[(Configuration, Dependency)] = Seq.empty,
      configurations: Map[Configuration, Seq[Configuration]] = Map.empty,
      properties: Seq[(String, String)] = Seq.empty,
      profiles: Seq[Profile] = Seq.empty,
      versions: Option[coursier.core.Versions] = None,
      snapshotVersioning: Option[coursier.core.SnapshotVersioning] = None,
      packaging: Option[Type] = None,
      relocated: Boolean = false,
      publications: Seq[(Configuration, coursier.core.Publication)] = Nil
    ): Project =
      coursier.core.Project(
        module,
        version,
        dependencies,
        configurations,
        parent,
        dependencyManagement,
        properties,
        profiles,
        versions,
        snapshotVersioning,
        packaging,
        relocated,
        None,
        publications,
        Info.empty
      )
  }
}
