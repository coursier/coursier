package coursier.core

import dataclass.data

object DependencyManagement {
  type Map = scala.Predef.Map[Key, Values]

  @data class Key(
    organization: Organization,
    name: ModuleName,
    `type`: Type,
    classifier: Classifier
  ) {
    def map(f: String => String): Key =
      Key(
        organization = organization.map(f),
        name = name.map(f),
        `type` = `type`.map(f),
        classifier = classifier.map(f)
      )
  }

  @data class Values(
    config: Configuration,
    version: String,
    minimizedExclusions: MinimizedExclusions,
    optional: Boolean
  ) {
    def isEmpty: Boolean =
      config.value.isEmpty && version.isEmpty && minimizedExclusions.isEmpty && !optional
    def fakeDependency(key: Key): Dependency =
      Dependency(
        Module(key.organization, key.name, Map.empty),
        version,
        config,
        minimizedExclusions,
        Publication("", key.`type`, Extension.empty, key.classifier),
        optional = optional,
        transitive = true
      )
    def orElse(other: Values): Values =
      Values(
        if (config.value.isEmpty) other.config else config,
        if (version.isEmpty) other.version else version,
        other.minimizedExclusions.join(minimizedExclusions),
        optional || other.optional
      )
    def mapButVersion(f: String => String): Values =
      Values(
        config = config.map(f),
        version = version,
        minimizedExclusions = minimizedExclusions.map(f),
        optional = optional // FIXME This might have been a string like "${some-prop}" initially :/
      )
    def mapVersion(f: String => String): Values =
      withVersion(f(version))
  }

  object Values {
    val empty = Values(
      config = Configuration.empty,
      version = "",
      minimizedExclusions = MinimizedExclusions.zero,
      optional = false
    )
  }
}
