package coursier.core

import coursier.version.{
  VersionConstraint => VersionConstraint0,
  VersionInterval => VersionInterval0
}
import dataclass.{data, since}

import scala.collection.mutable

object DependencyManagement {
  type Map        = scala.collection.immutable.Map[Key, Values]
  type GenericMap = scala.collection.Map[Key, Values]

  @data class Key(
    organization: Organization,
    name: ModuleName,
    `type`: Type,
    classifier: Classifier
  ) {
    def map(f: String => String): Key = {
      val newOrg        = organization.map(f)
      val newName       = name.map(f)
      val newType       = `type`.map(f)
      val newClassifier = classifier.map(f)
      if (
        organization != newOrg || name != newName || `type` != newType || classifier != newClassifier
      )
        Key(
          organization = newOrg,
          name = newName,
          `type` = newType,
          classifier = newClassifier
        )
      else
        this
    }

    def fakeModule: Module =
      Module(organization, name, Map.empty)

    // Mainly there for sorting purposes
    def repr: String =
      s"${organization.value}:${name.value}:${`type`.value}:${classifier.value}"
  }

  object Key {
    def from(dep: Dependency): Key =
      dep.depManagementKey
  }

  @data class Values(
    config: Configuration,
    versionConstraint: VersionConstraint0,
    minimizedExclusions: MinimizedExclusions,
    optional: Boolean,
    @since("2.1.25")
    global: Boolean = false
  ) {

    @deprecated("Use the override accepting a VersionConstraint instead", "2.1.25")
    def this(
      config: Configuration,
      version: String,
      minimizedExclusions: MinimizedExclusions,
      optional: Boolean
    ) = this(
      config,
      VersionConstraint0(version),
      minimizedExclusions,
      optional
    )

    @deprecated("Use versionConstraint instead", "2.1.25")
    def version: String =
      versionConstraint.asString
    @deprecated("Use withVersionConstraint instead", "2.1.25")
    def withVersion(newVersion: String): Values =
      if (newVersion == version) this
      else withVersionConstraint(VersionConstraint0(newVersion))

    def isEmpty: Boolean =
      config.value.isEmpty && versionConstraint.asString.isEmpty && minimizedExclusions.isEmpty && !optional
    def fakeDependency(key: Key): Dependency =
      Dependency(
        key.fakeModule,
        versionConstraint,
        VariantSelector.ConfigurationBased(config),
        minimizedExclusions,
        Publication("", key.`type`, Extension.empty, key.classifier),
        optional = optional,
        transitive = true
      )
    def orElse(other: Values): Values = {
      val newConfig = if (config.value.isEmpty) other.config else config
      val newVersion =
        if (versionConstraint.asString.isEmpty) other.versionConstraint else versionConstraint
      val newExcl     = other.minimizedExclusions.join(minimizedExclusions)
      val newOptional = optional || other.optional
      if (
        config != newConfig || versionConstraint != newVersion || minimizedExclusions != newExcl || optional != newOptional
      )
        Values(
          newConfig,
          newVersion,
          newExcl,
          newOptional
        )
      else
        this
    }
    def mapButVersion(f: String => String): Values = {
      val newConfig = config.map(f)
      val newExcl   = minimizedExclusions.map(f)
      if (config != newConfig || minimizedExclusions != newExcl)
        Values(
          config = newConfig,
          versionConstraint = versionConstraint,
          minimizedExclusions = newExcl,
          // FIXME This might have been a string like "${some-prop}" initially :/
          optional = optional
        )
      else
        this
    }
    def mapVersion(f: String => String): Values = {
      val newVersion = f(versionConstraint.asString)
      if (versionConstraint.asString == newVersion) this
      else withVersionConstraint(VersionConstraint0(newVersion))
    }

    override def toString(): String = {
      var fields = Seq(
        config.toString,
        versionConstraint.toString,
        minimizedExclusions.toString,
        optional.toString
      )
      if (global)
        fields = fields :+ global.toString
      fields.mkString("Values(", ", ", ")")
    }
  }

  object Values {
    val empty = Values(
      config = Configuration.empty,
      versionConstraint = VersionConstraint0.empty,
      minimizedExclusions = MinimizedExclusions.zero,
      optional = false
    )

    def from(config: Configuration, dep: Dependency): Values =
      Values(
        config,
        dep.versionConstraint,
        dep.minimizedExclusions,
        dep.optional
      )

    @deprecated("Use the override accepting a VersionConstraint instead", "2.1.25")
    def apply(
      config: Configuration,
      version: String,
      minimizedExclusions: MinimizedExclusions,
      optional: Boolean
    ): Values = apply(
      config,
      VersionConstraint0(version),
      minimizedExclusions,
      optional
    )
  }

  def entry(config: Configuration, dep: Dependency): (Key, Values) =
    (Key.from(dep), Values.from(config, dep))

  /** Converts a sequence of dependency management entries to a dependency management map
    *
    * The map having at most one value per key, rather than possibly several in the sequence
    *
    * This composes the values together, keeping the version of the first one, and adding their
    * exclusions if `composeValues` is true (the default). In particular, this respects the order of
    * values in the incoming sequence, and makes sure the values in the initial map go before those
    * of the sequence.
    */
  def add(
    initialMap: Map,
    entries: Seq[(Key, Values)],
    composeValues: Boolean = true
  ): Map =
    if (entries.isEmpty)
      initialMap
    else {
      val b = new mutable.HashMap[Key, Values]
      b.sizeHint(initialMap.size + entries.length)
      b ++= initialMap
      val it = entries.iterator
      while (it.hasNext) {
        val (key0, incomingValues) = it.next()
        val newValues = b.get(key0) match {
          case Some(previousValues) =>
            if (composeValues) previousValues.orElse(incomingValues)
            else previousValues
          case None =>
            incomingValues
        }
        b += ((key0, newValues))
      }
      b.result().toMap
    }

  def addAll(
    initialMap: Map,
    entries: Seq[GenericMap],
    composeValues: Boolean = true
  ): GenericMap =
    if (entries.forall(_.isEmpty))
      initialMap
    else {
      val b = new mutable.HashMap[Key, Values]
      b.sizeHint(entries.iterator.map(_.size).sum)
      val it = entries.iterator.flatMap(_.iterator)
      while (it.hasNext) {
        val (key0, incomingValues) = it.next()
        val newValuesOpt = b.get(key0).orElse(initialMap.get(key0)) match {
          case Some(previousValues) =>
            if (composeValues)
              Some(previousValues.orElse(incomingValues))
                .filter(_ != previousValues)
            else
              None
          case None =>
            Some(incomingValues)
        }
        for (newValues <- newValuesOpt)
          b += ((key0, newValues))
      }
      if (b.isEmpty) initialMap
      else if (initialMap.isEmpty) b
      else initialMap ++ b
    }

  def addDependencies(
    map: Map,
    deps: Seq[(Configuration, Dependency)],
    composeValues: Boolean = true
  ): Map =
    add(
      map,
      deps.map {
        case (config, dep) =>
          entry(config, dep)
      },
      composeValues = composeValues
    )
}
