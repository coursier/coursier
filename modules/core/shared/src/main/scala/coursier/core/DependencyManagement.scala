package coursier.core

import dataclass.data

object DependencyManagement {
  type Map = scala.Predef.Map[Key, Values]

  @data class Key(
    organization: Organization,
    name: ModuleName,
    `type`: Type,
    classifier: Classifier
  )

  @data class Values(
    config: Configuration,
    version: String,
    minimizedExclusions: MinimizedExclusions,
    optional: Boolean
  ) {
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
  }
}
