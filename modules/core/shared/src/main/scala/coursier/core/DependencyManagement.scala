package coursier.core

import coursier.version.{
  VersionConstraint => VersionConstraint0,
  VersionInterval => VersionInterval0
}
import dataclass.{data, since}

import scala.collection.mutable

object DependencyManagement {
  @data class Key(
    organization: Organization,
    name: ModuleName,
    `type`: Type,
    classifier: Classifier
  ) {
    def hasProperties: Boolean =
      organization.value.contains("$") ||
      name.value.contains("$") ||
      classifier.value.contains("$") ||
      `type`.value.contains("$")
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

  final case class SplitValues(
    map: Map[Values.ValuesCore, Values.ValuesMergeable]
  ) {
    assert(map.nonEmpty)

    def hasProperties: Boolean =
      map.exists(_._1.config.value.contains("$")) ||
      map.exists(_._2.versionConstraints.exists(_.exists(_.asString.contains("$")))) ||
      map.exists(_._2.minimizedExclusions.hasProperties)

    def mapOnValues(f: Values => Values): SplitValues = {
      val values    = list
      val newValues = list.map(f)
      if (newValues == values) this
      else
        SplitValues {
          newValues.toSeq
            .map(_.coreMergeable)
            .groupBy(_._1)
            .map {
              case (k, l) =>
                k -> Values.ValuesMergeable.merge(l.map(_._2))
            }
        }
    }

    def list: Set[Values] =
      map
        .iterator
        .map {
          case (k, v) =>
            v.versionConstraints.map { versionConstraintOpt =>
              Values(
                k.config,
                versionConstraintOpt.getOrElse(VersionConstraint0.empty),
                v.minimizedExclusions,
                v.optional,
                k.global
              )
            }
        }
        .foldLeft(Set.empty[Values])(_ ++ _)

    def add(other: SplitValues): SplitValues = {
      val m = new mutable.HashMap[Values.ValuesCore, Values.ValuesMergeable] ++ map
      for ((k, v) <- other.map)
        m(k) = m.get(k).fold(v)(v0 => Values.ValuesMergeable.merge(Seq(v0, v)))
      SplitValues(m.toMap)
    }

    def orElse(other: SplitValues): SplitValues = {
      val map = list
        .iterator
        .flatMap { v =>
          other.list.iterator.map(v0 => v.orElse(v0))
        }
        .map(_.coreMergeable)
        .toSeq
        .groupBy(_._1)
        .map {
          case (k, l) =>
            (k, Values.ValuesMergeable.merge(l.map(_._2)))
        }
      SplitValues(map)
    }

    def withGlobal(global: Boolean): SplitValues =
      if (map.exists(_._1.global != global))
        SplitValues(
          map.map {
            case (k, v) =>
              (k.withGlobal(global), v)
          }
        )
      else
        this

    def contains(other: SplitValues): Boolean =
      other.map.forall {
        case (k, v) =>
          map.get(k).exists(_.contains(v))
      }
  }

  @data class Values(
    config: Configuration,
    versionConstraint: VersionConstraint0,
    minimizedExclusions: MinimizedExclusions,
    optional: Boolean,
    @since("2.1.25")
    global: Boolean = false
  ) {
    def core: Values.ValuesCore = Values.ValuesCore(config, global)
    def mergeable: Values.ValuesMergeable =
      Values.ValuesMergeable(
        Set(Some(versionConstraint).filter(_.asString.nonEmpty)),
        minimizedExclusions,
        optional
      )
    def coreMergeable: (Values.ValuesCore, Values.ValuesMergeable) =
      core -> mergeable
    def splitValues: SplitValues =
      SplitValues(Map(coreMergeable))

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
        Publication("", key.`type`, Extension.empty, key.classifier),
        optional = optional,
        transitive = true,
        Nil,
        Overrides.empty.addExclusions(minimizedExclusions),
        endorseStrictVersions = false
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

    def repr: String = {
      val parts = Seq.newBuilder[String]
      parts += versionConstraint.asString
      if (config.value.nonEmpty) parts += s"config:${config.value}"
      if (optional) parts += "optional"
      if (global) parts += "global"
      val exclusionsPart =
        if (minimizedExclusions.isEmpty)
          ""
        else
          "\n" + minimizedExclusions.repr.linesWithSeparators.map("    " + _).mkString
      parts.result().mkString(" ") + exclusionsPart
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
    def from(config: Configuration, dep: Dependency): Values = {
      if (dep.overridesMap.map.size > 1)
        sys.error("Need to update DependencyManagement.Values.from")
      Values(
        config,
        dep.versionConstraint,
        dep.overridesMap.map.head._1,
        dep.optional
      )
    }

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

    @data class ValuesCore(
      config: Configuration,
      global: Boolean
    )
    @data class ValuesMergeable(
      versionConstraints: Set[Option[VersionConstraint0]],
      minimizedExclusions: MinimizedExclusions,
      optional: Boolean
    ) {
      assert(versionConstraints.forall(_.forall(_.asString.nonEmpty)))
      def normalizeVersionConstraints: ValuesMergeable = {
        val newVersionConstraints0 =
          if (versionConstraints.size <= 1) versionConstraints
          else {
            val (withPropsOrEmpty, withoutProps) =
              versionConstraints.partition(_.forall(_.asString.contains("${")))
            if (withoutProps.size <= 1)
              versionConstraints
            else
              VersionConstraint0.merge(withoutProps.toSeq.flatten: _*) match {
                case None =>
                  versionConstraints
                case Some(m) =>
                  withPropsOrEmpty ++ Set(Some(m))
              }
          }
        if (versionConstraints eq newVersionConstraints0) this
        else withVersionConstraints(newVersionConstraints0)
      }
      def isEmpty: Boolean =
        versionConstraints.isEmpty && minimizedExclusions.isEmpty && !optional
      def add(other: ValuesMergeable): ValuesMergeable =
        if (other.isEmpty) this
        else if (isEmpty) other
        else {
          val newVersionConstraints = versionConstraints ++ other.versionConstraints
          val newExclusions         = minimizedExclusions.meet(other.minimizedExclusions)
          val newOptional           = optional || other.optional
          if (
            versionConstraints.size != newVersionConstraints.size || minimizedExclusions != newExclusions || optional != newOptional
          )
            ValuesMergeable(
              newVersionConstraints,
              newExclusions,
              newOptional
            ).normalizeVersionConstraints
          else
            this
        }

      def contains(other: ValuesMergeable): Boolean =
        other.versionConstraints.subsetOf(versionConstraints) &&
        other.minimizedExclusions.subsetOf(minimizedExclusions) &&
        (!other.optional || optional)
    }

    object ValuesMergeable {
      val empty = ValuesMergeable(
        versionConstraints = Set.empty,
        minimizedExclusions = MinimizedExclusions.zero,
        optional = false
      )
      def merge(mergeables: Seq[ValuesMergeable]): ValuesMergeable = {
        val mergeables0 = mergeables.filter(_ != empty)
        if (mergeables0.isEmpty) empty
        else if (mergeables0.length == 1) mergeables0.head
        else {
          var newVersionConstraints = Set.empty[Option[VersionConstraint0]]
          var newExclusions         = MinimizedExclusions.zero
          var newOptional           = false
          for (elem <- mergeables0) {
            newVersionConstraints = newVersionConstraints ++ elem.versionConstraints
            newExclusions = newExclusions.join(elem.minimizedExclusions)
            newOptional = newOptional || elem.optional
          }
          if (newVersionConstraints.nonEmpty || newExclusions.nonEmpty || newOptional)
            ValuesMergeable(
              newVersionConstraints,
              newExclusions,
              newOptional
            ).normalizeVersionConstraints
          else
            empty
        }
      }
    }
  }

  def entry(config: Configuration, dep: Dependency): (Key, Values) =
    (Key.from(dep), Values.from(config, dep))
}
