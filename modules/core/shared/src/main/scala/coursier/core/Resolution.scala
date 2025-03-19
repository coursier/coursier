package coursier.core

import coursier.error.{DependencyError, VariantError}
import coursier.util.Artifact
import coursier.version.{
  ConstraintReconciliation,
  Version => Version0,
  VersionConstraint => VersionConstraint0
}
import dataclass.data

import java.util.concurrent.ConcurrentHashMap

import scala.annotation.tailrec
import scala.collection.compat._
import scala.collection.compat.immutable.LazyList
import scala.collection.mutable
import scala.jdk.CollectionConverters._

object Resolution {

  type ModuleVersion           = (Module, String)
  type ModuleVersionConstraint = (Module, VersionConstraint0)

  def profileIsActive0(
    profile: Profile,
    properties: Map[String, String],
    osInfo: Activation.Os,
    jdkVersion: Option[Version0],
    userActivations: Option[Map[String, Boolean]]
  ): Boolean = {

    val fromUserOrDefault = userActivations match {
      case Some(activations) =>
        activations.get(profile.id)
      case None =>
        profile.activeByDefault
          .filter(identity)
    }

    def fromActivation = profile.activation.isActive(properties, osInfo, jdkVersion)

    fromUserOrDefault.getOrElse(fromActivation)
  }

  @deprecated("Use profileIsActive0 instead", "2.1.25")
  def profileIsActive(
    profile: Profile,
    properties: Map[String, String],
    osInfo: Activation.Os,
    jdkVersion: Option[String],
    userActivations: Option[Map[String, Boolean]]
  ): Boolean =
    profileIsActive0(
      profile,
      properties,
      osInfo,
      jdkVersion.map(Version0(_)),
      userActivations
    )

  /** Get the active profiles of `project`, using the current properties `properties`, and
    * `profileActivations` stating if a profile is active.
    */
  def profiles0(
    project: Project,
    properties: Map[String, String],
    osInfo: Activation.Os,
    jdkVersion: Option[Version0],
    userActivations: Option[Map[String, Boolean]]
  ): Seq[Profile] =
    project.profiles.filter { profile =>
      profileIsActive0(
        profile,
        properties,
        osInfo,
        jdkVersion,
        userActivations
      )
    }

  @deprecated("Use profiles0 instead", "2.1.25")
  def profiles(
    project: Project,
    properties: Map[String, String],
    osInfo: Activation.Os,
    jdkVersion: Option[String],
    userActivations: Option[Map[String, Boolean]]
  ): Seq[Profile] =
    profiles0(
      project,
      properties,
      osInfo,
      jdkVersion.map(Version0(_)),
      userActivations
    )

  def addDependencies(
    deps: Seq[Seq[(Variant, Dependency)]]
  ): Seq[(Variant, Dependency)] = {

    val it  = deps.reverseIterator
    var set = Set.empty[DependencyManagement.Key]
    var acc = Seq.empty[(Variant, Dependency)]
    while (it.hasNext) {
      val deps0 = it.next()
      val deps = deps0.filter {
        case (_, dep) =>
          !set(DependencyManagement.Key.from(dep))
      }
      acc = if (acc.isEmpty) deps else acc ++ deps
      if (it.hasNext)
        set = set ++ deps.map { case (_, dep) => DependencyManagement.Key.from(dep) }
    }

    acc
  }

  def hasProps(s: String): Boolean = {

    var ok  = false
    var idx = 0

    while (idx < s.length && !ok) {
      var dolIdx = idx
      while (dolIdx < s.length && s.charAt(dolIdx) != '$')
        dolIdx += 1
      idx = dolIdx

      if (dolIdx < s.length - 2 && s.charAt(dolIdx + 1) == '{') {
        var endIdx = dolIdx + 2
        while (endIdx < s.length && s.charAt(endIdx) != '}')
          endIdx += 1
        if (endIdx < s.length) {
          assert(s.charAt(endIdx) == '}')
          ok = true
        }
      }

      if (!ok && idx < s.length) {
        assert(s.charAt(idx) == '$')
        idx += 1
      }
    }

    ok
  }

  def substituteProps(s: String, properties: Map[String, String]): String =
    substituteProps(s, properties, trim = false)

  def substituteProps(s: String, properties: Map[String, String], trim: Boolean): String = {

    // this method is called _very_ often, hence the micro-optimization

    var b: java.lang.StringBuilder = null
    var idx                        = 0

    while (idx < s.length) {
      var dolIdx = idx
      while (dolIdx < s.length && s.charAt(dolIdx) != '$')
        dolIdx += 1
      if (idx != 0 || dolIdx < s.length) {
        if (b == null)
          b = new java.lang.StringBuilder(s.length + 32)
        b.append(s, idx, dolIdx)
      }
      idx = dolIdx

      var name: String = null
      if (dolIdx < s.length - 2 && s.charAt(dolIdx + 1) == '{') {
        var endIdx = dolIdx + 2
        while (endIdx < s.length && s.charAt(endIdx) != '}')
          endIdx += 1
        if (endIdx < s.length) {
          assert(s.charAt(endIdx) == '}')
          name = s.substring(dolIdx + 2, endIdx)
        }
      }

      if (name == null) {
        if (idx < s.length) {
          assert(s.charAt(idx) == '$')
          b.append('$')
          idx += 1
        }
      }
      else {
        idx = idx + 2 + name.length + 1 // == endIdx + 1
        properties.get(name) match {
          case None =>
            b.append(s, dolIdx, idx)
          case Some(v) =>
            val v0 = if (trim) v.trim else v
            b.append(v0)
        }
      }
    }

    if (b == null)
      s
    else
      b.toString
  }

  def withProperties0(
    dependencies: Seq[(Variant, Dependency)],
    properties: Map[String, String]
  ): Seq[(Variant, Dependency)] =
    dependencies.map(withProperties(_, properties))

  @deprecated("Use withProperties0 instead", "2.1.25")
  def withProperties(
    dependencies: Seq[(Configuration, Dependency)],
    properties: Map[String, String]
  ): Seq[(Configuration, Dependency)] =
    withProperties0(
      dependencies.map {
        case (config, dep) =>
          (Variant.Configuration(config), dep)
      },
      properties
    ).map {
      case (c: Variant.Configuration, dep) =>
        (c.configuration, dep)
      case (_: Variant.Attributes, _) =>
        sys.error("Deprecated method doesn't support Gradle Module variants")
    }

  private def withProperties(
    map: DependencyManagement.GenericMap,
    properties: Map[String, String]
  ): Option[DependencyManagement.Map] = {
    val b       = new mutable.HashMap[DependencyManagement.Key, DependencyManagement.Values]
    var changed = false
    for (kv <- map) {
      val (k0, v0) = withProperties(kv, properties)
      if (!changed && (k0 != kv._1 || v0 != kv._2))
        changed = true
      b(k0) = b.get(k0).fold(v0)(_.orElse(v0))
    }
    if (changed) Some(b.toMap)
    else None
  }

  private def withProperties(
    overrides: Overrides,
    properties: Map[String, String]
  ): Overrides =
    if (overrides.hasProperties)
      overrides.mapMap(withProperties(_, properties))
    else
      overrides

  private def withProperties(
    entry: (DependencyManagement.Key, DependencyManagement.Values),
    properties: Map[String, String]
  ): (DependencyManagement.Key, DependencyManagement.Values) = {

    def substituteTrimmedProps(s: String) =
      substituteProps(s, properties, trim = true)
    def substituteProps0(s: String) =
      substituteProps(s, properties, trim = false)

    val (key, values) = entry

    (
      key.map(substituteProps0),
      values.mapButVersion(substituteProps0).mapVersion(substituteTrimmedProps)
    )
  }

  /** Substitutes `properties` in `dependencies`.
    */
  private def withProperties(
    variantDep: (Variant, Dependency),
    properties: Map[String, String]
  ): (Variant, Dependency) = {

    val (variant, dep) = variantDep

    if (variant.asConfiguration.exists(_.value.contains("$")) || dep.hasProperties) {

      def substituteTrimmedProps(s: String) =
        substituteProps(s, properties, trim = true)
      def substituteProps0(s: String) =
        substituteProps(s, properties, trim = false)

      val dep0 = dep
        .withVersionConstraint(
          if (dep.versionConstraint.asString.contains("$"))
            VersionConstraint0(substituteTrimmedProps(dep.versionConstraint.asString))
          else
            dep.versionConstraint
        )
        .copy(
          module = dep.module.copy(
            organization = dep.module.organization.map(substituteProps0),
            name = dep.module.name.map(substituteProps0)
          ),
          attributes = dep.attributes
            .withType(dep.attributes.`type`.map(substituteProps0))
            .withClassifier(dep.attributes.classifier.map(substituteProps0)),
          variantSelector = dep.variantSelector
            .asConfiguration
            .map(_.map(substituteProps0))
            .map(VariantSelector.ConfigurationBased(_))
            .getOrElse(dep.variantSelector),
          minimizedExclusions = dep.minimizedExclusions.map(substituteProps0)
        )

      val finalVariant = variant
        .asConfiguration
        .map(_.map(substituteProps0))
        .map(Variant.Configuration(_))
        .getOrElse(variant)

      // FIXME The content of the optional tag may also be a property in
      // the original POM. Maybe not parse it that earlier?
      finalVariant -> dep0
    }
    else
      variantDep
  }

  /** Merge several dependencies, solving version constraints of duplicated modules.
    *
    * Returns the conflicted dependencies, and the merged others.
    */
  def merge0(
    dependencies: Seq[Dependency],
    forceVersions: Map[Module, VersionConstraint0],
    reconciliation: Option[Module => ConstraintReconciliation],
    preserveOrder: Boolean = false
  ): (Seq[Dependency], Seq[Dependency], Map[Module, VersionConstraint0]) = {
    def reconcilerByMod(mod: Module): ConstraintReconciliation =
      reconciliation match {
        case Some(f) => f(mod)
        case _       => ConstraintReconciliation.Default
      }
    val dependencies0 = dependencies.toVector
    val mergedByModVer = dependencies0
      .groupBy(dep => dep.module)
      .map { case (module, deps) =>
        val forcedVersionOpt = forceVersions.get(module)
          .orElse(forceVersions.get(module.withOrganization(Organization("*"))))

        module -> {
          forcedVersionOpt match {
            case None =>
              if (deps.lengthCompare(1) == 0) (Right(deps), Some(deps.head.versionConstraint))
              else {
                val versions0  = deps.map(_.versionConstraint)
                val reconciler = reconcilerByMod(module)
                val versionOpt = reconciler.reconcile(versions0)

                (
                  versionOpt match {
                    case Some(version) => Right(deps.map(_.withVersionConstraint(version)))
                    case None          => Left(deps)
                  },
                  versionOpt
                )
              }

            case Some(forcedVersion) =>
              (Right(deps.map(_.withVersionConstraint(forcedVersion))), Some(forcedVersion))
          }
        }
      }

    val merged =
      if (preserveOrder)
        dependencies0
          .map(_.module)
          .distinct
          .map(mergedByModVer(_))
      else
        mergedByModVer
          .values
          .toVector

    (
      merged
        .collect { case (Left(dep), _) => dep }
        .flatten,
      merged
        .collect { case (Right(dep), _) => dep }
        .flatten,
      mergedByModVer
        .collect { case (mod, (_, Some(ver))) => mod -> ver }
    )
  }

  @deprecated("", "2.1.25")
  def merge(
    dependencies: Seq[Dependency],
    forceVersions: Map[Module, String],
    reconciliation: Option[Module => coursier.core.Reconciliation],
    preserveOrder: Boolean = false
  ): (Seq[Dependency], Seq[Dependency], Map[Module, String]) = {
    val (a, b, c) = merge0(
      dependencies,
      forceVersions.map {
        case (mod, ver) =>
          (mod, VersionConstraint0(ver))
      },
      reconciliation.map { f => mod =>
        ConstraintReconciliation(f(mod).id).getOrElse {
          sys.error("Cannot happen")
        }
      },
      preserveOrder
    )
    (
      a,
      b,
      c.map {
        case (mod, ver) =>
          (mod, ver.asString)
      }
    )
  }

  /** Applies `dependencyManagement` to `dependencies`.
    *
    * Fill empty version / scope / exclusions, for dependencies found in `dependencyManagement`.
    */
  def depsWithDependencyManagement0(
    rawDependencies: Seq[(Variant, Dependency)],
    properties: Map[String, String],
    rawOverridesOpt: Option[Overrides],
    rawDependencyManagement: Overrides,
    forceDepMgmtVersions: Boolean,
    keepVariant: Variant => Boolean
  ): Seq[(Variant, Dependency)] = {

    val dependencies         = withProperties0(rawDependencies, properties)
    val overridesOpt         = rawOverridesOpt.map(withProperties(_, properties))
    val dependencyManagement = withProperties(rawDependencyManagement, properties)

    // See http://maven.apache.org/guides/introduction/introduction-to-dependency-mechanism.html#Dependency_Management

    lazy val dict = Overrides.add(
      overridesOpt.getOrElse(Overrides.empty),
      dependencyManagement
    )

    lazy val dictForOverridesOpt = rawOverridesOpt.map { rawOverrides =>
      lazy val versions = dependencies
        .filter {
          case (variant, _) =>
            variant.isEmpty || keepVariant(variant)
        }
        .groupBy(_._2.depManagementKey)
        .collect {
          case (k, l)
              if !rawOverrides.contains(k) && l.exists(_._2.versionConstraint.asString.nonEmpty) =>
            k -> l.map(_._2.versionConstraint.asString).filter(_.nonEmpty)
        }
      val map = dependencyManagement
        .filter {
          case (k, v) =>
            v.config.isEmpty || keepVariant(Variant.Configuration(v.config))
        }
        .map {
          case (k, v) =>
            val clearVersion = !forceDepMgmtVersions &&
              versions
                .get(k)
                .getOrElse(Nil)
                .exists(_ != v.versionConstraint.asString)
            val newConfig  = Configuration.empty
            val newVersion = if (clearVersion) VersionConstraint0.empty else v.versionConstraint
            val values =
              if (v.config != newConfig || v.versionConstraint != newVersion || v.optional)
                DependencyManagement.Values(
                  newConfig,
                  newVersion,
                  v.minimizedExclusions,
                  optional = false
                )
              else
                v
            (k, values)
        }
      Overrides.add(
        rawOverrides,
        map
      )
    }

    dependencies.map {
      case (variant0, dep0) =>
        var variant = variant0
        var dep     = dep0

        for (mgmtValues <- dict.get(dep0.depManagementKey)) {

          val useManagedVersion = mgmtValues.versionConstraint.asString.nonEmpty && (
            forceDepMgmtVersions ||
            overridesOpt.isEmpty ||
            dep.versionConstraint.asString.isEmpty ||
            overridesOpt.exists(_.contains(dep0.depManagementKey))
          )
          if (useManagedVersion)
            dep = dep.withVersionConstraint(mgmtValues.versionConstraint)

          if (mgmtValues.minimizedExclusions.nonEmpty) {
            val newExcl = dep.minimizedExclusions.join(mgmtValues.minimizedExclusions)
            if (dep.minimizedExclusions != newExcl)
              dep = dep.withMinimizedExclusions(newExcl)
          }
        }

        for (mgmtValues <- dependencyManagement.get(dep0.depManagementKey)) {

          if (mgmtValues.config.nonEmpty && variant.isEmpty)
            variant = Variant.Configuration(mgmtValues.config)

          if (mgmtValues.optional && !dep.optional)
            dep = dep.withOptional(mgmtValues.optional)
        }

        for (dictForOverrides <- dictForOverridesOpt if dictForOverrides.nonEmpty) {
          val newOverrides = Overrides.add(dictForOverrides, dep.overridesMap)
          if (dep.overridesMap != newOverrides)
            dep = dep.withOverridesMap(newOverrides)
        }

        (variant, dep)
    }
  }

  @deprecated("Use depsWithDependencyManagement0 instead", "2.1.25")
  def depsWithDependencyManagement(
    rawDependencies: Seq[(Configuration, Dependency)],
    properties: Map[String, String],
    rawOverridesOpt: Option[Overrides],
    rawDependencyManagement: Overrides,
    forceDepMgmtVersions: Boolean,
    keepVariant: Variant => Boolean
  ): Seq[(Configuration, Dependency)] =
    depsWithDependencyManagement0(
      rawDependencies.map {
        case (config, dep) =>
          (Variant.Configuration(config), dep)
      },
      properties,
      rawOverridesOpt,
      rawDependencyManagement,
      forceDepMgmtVersions,
      keepVariant
    ).map {
      case (c: Variant.Configuration, dep) =>
        (c.configuration, dep)
      case (_: Variant.Attributes, _) =>
        sys.error("Deprecated method doesn't support Gradle Module variants")
    }

  @deprecated(
    "Use the override accepting Overrides and keepConfiguration instead",
    "2.1.23"
  )
  def depsWithDependencyManagement(
    rawDependencies: Seq[(Configuration, Dependency)],
    properties: Map[String, String],
    rawOverridesOpt: Option[DependencyManagement.Map],
    rawDependencyManagement: Seq[(Configuration, Dependency)],
    forceDepMgmtVersions: Boolean
  ): Seq[(Configuration, Dependency)] =
    depsWithDependencyManagement0(
      rawDependencies.map {
        case (config, dep) =>
          (Variant.Configuration(config), dep)
      },
      properties,
      rawOverridesOpt.map(Overrides(_)),
      Overrides(DependencyManagement.addDependencies(Map.empty, rawDependencyManagement)),
      forceDepMgmtVersions,
      keepVariant = (variant: Variant) =>
        variant.asConfiguration.exists { config =>
          config == Configuration.compile ||
          config == Configuration.runtime ||
          config == Configuration.default ||
          config == Configuration.defaultCompile ||
          config == Configuration.defaultRuntime
        }
    ).map {
      case (variant: Variant.Configuration, dep) =>
        (variant.configuration, dep)
      case (other, _) =>
        sys.error("Deprecated method doesn't handle generic variants")
    }

  @deprecated(
    "Use the override accepting an override map, forceDepMgmtVersions, and properties instead",
    "2.1.17"
  )
  def depsWithDependencyManagement(
    dependencies: Seq[(Configuration, Dependency)],
    dependencyManagement: Seq[(Configuration, Dependency)]
  ): Seq[(Configuration, Dependency)] =
    depsWithDependencyManagement0(
      dependencies.map {
        case (config, dep) =>
          (Variant.Configuration(config), dep)
      },
      Map.empty[String, String],
      Option.empty[Overrides],
      Overrides(DependencyManagement.addDependencies(Map.empty, dependencyManagement)),
      forceDepMgmtVersions = false,
      keepVariant = (variant: Variant) =>
        variant.asConfiguration.exists { config =>
          config == Configuration.compile ||
          config == Configuration.default ||
          config == Configuration.defaultCompile
        }
    ).map {
      case (variant: Variant.Configuration, dep) =>
        (variant.configuration, dep)
      case (other, dep) =>
        sys.error("Deprecated method doesn't handle generic variants")
    }

  private def withDefaultConfig(dep: Dependency, defaultConfiguration: Configuration): Dependency =
    if (dep.variantSelector.asConfiguration.exists(_.isEmpty))
      dep.withVariantSelector(VariantSelector.ConfigurationBased(defaultConfiguration))
    else
      dep

  /** Filters `dependencies` with `exclusions`.
    */
  def withExclusions0(
    dependencies: Seq[(Variant, Dependency)],
    exclusions: Set[(Organization, ModuleName)]
  ): Seq[(Variant, Dependency)] = {
    val minimizedExclusions = MinimizedExclusions(exclusions)

    dependencies
      .filter {
        case (_, dep) =>
          minimizedExclusions(dep.module.organization, dep.module.name)
      }
      .map {
        case configDep @ (config, dep) =>
          val newExcl = dep.minimizedExclusions.join(minimizedExclusions)
          if (dep.minimizedExclusions == newExcl) configDep
          else config -> dep.withMinimizedExclusions(newExcl)
      }
  }

  @deprecated("Use withExclusions0 instead", "2.1.25")
  def withExclusions(
    dependencies: Seq[(Configuration, Dependency)],
    exclusions: Set[(Organization, ModuleName)]
  ): Seq[(Configuration, Dependency)] =
    withExclusions0(
      dependencies.map {
        case (config, dep) =>
          (Variant.Configuration(config), dep)
      },
      exclusions
    ).map {
      case (c: Variant.Configuration, dep) =>
        (c.configuration, dep)
      case (_: Variant.Attributes, _) =>
        sys.error("Deprecated method doesn't support Gradle Module variants")
    }

  def actualConfiguration(
    config: Configuration,
    configurations: Map[Configuration, Seq[Configuration]]
  ): Configuration =
    actualConfiguration(config, configurations.keySet)

  def actualConfiguration(
    config: Configuration,
    configurations: Set[Configuration]
  ): Configuration =
    Parse.withFallbackConfig(config) match {
      case Some((main, fallback)) =>
        if (configurations.contains(main))
          main
        else if (configurations.contains(fallback))
          fallback
        else
          config
      case None => config
    }

  def parentConfigurations(
    actualConfig: Configuration,
    configurations: Map[Configuration, Seq[Configuration]]
  ): Set[Configuration] = {
    @tailrec
    def helper(configs: Set[Configuration], acc: Set[Configuration]): Set[Configuration] =
      if (configs.isEmpty)
        acc
      else if (configs.exists(acc))
        helper(configs -- acc, acc)
      else if (configs.exists(!configurations.contains(_))) {
        val (remaining, notFound) = configs.partition(configurations.contains)
        helper(remaining, acc ++ notFound)
      }
      else {
        val extraConfigs = configs.flatMap(configurations)
        helper(extraConfigs, acc ++ configs)
      }

    helper(Set(actualConfig), Set.empty)
  }

  @deprecated("Unused by coursier, should be removed in the future", "2.1.25")
  def withParentConfigurations(
    config: Configuration,
    configurations: Map[Configuration, Seq[Configuration]]
  ): (Configuration, Set[Configuration]) = {
    val config0 = actualConfiguration(config, configurations)
    (config0, parentConfigurations(config0, configurations))
  }

  private def staticProjectProperties(project: Project): Seq[(String, String)] =
    // FIXME The extra properties should only be added for Maven projects, not Ivy ones
    Seq(
      // some artifacts seem to require these (e.g. org.jmock:jmock-legacy:2.5.1)
      // although I can find no mention of them in any manual / spec
      "pom.groupId"    -> project.module.organization.value,
      "pom.artifactId" -> project.module.name.value,
      "pom.version"    -> project.actualVersion0.repr,
      // Required by some dependencies too (org.apache.directory.shared:shared-ldap:0.9.19 in particular)
      "groupId"            -> project.module.organization.value,
      "artifactId"         -> project.module.name.value,
      "version"            -> project.actualVersion0.asString,
      "project.groupId"    -> project.module.organization.value,
      "project.artifactId" -> project.module.name.value,
      "project.version"    -> project.actualVersion0.asString,
      "project.packaging"  -> project.packagingOpt.getOrElse(Type.jar).value
    ) ++ project.parent0.toSeq.flatMap {
      case (parModule, parVersion) =>
        Seq(
          "project.parent.groupId"    -> parModule.organization.value,
          "project.parent.artifactId" -> parModule.name.value,
          "project.parent.version"    -> parVersion.asString,
          "parent.groupId"            -> parModule.organization.value,
          "parent.artifactId"         -> parModule.name.value,
          "parent.version"            -> parVersion.asString
        )
    }

  def projectProperties(project: Project): Seq[(String, String)] =
    // loose attempt at substituting properties in each others in properties0
    // doesn't try to go recursive for now, but that could be made so if necessary
    substitute(project.properties ++ staticProjectProperties(project))

  private def substitute(properties0: Seq[(String, String)]): Seq[(String, String)] = {

    val done = properties0
      .iterator
      .collect {
        case kv @ (_, value) if !hasProps(value) =>
          kv
      }
      .toMap

    var didSubstitutions = false

    val res = properties0.map {
      case (k, v) =>
        val res = substituteProps(v, done)
        if (!didSubstitutions)
          didSubstitutions = res != v
        k -> res
    }

    if (didSubstitutions)
      substitute(res)
    else
      res
  }

  private def parents(
    project: Project,
    projectCache: ((Module, VersionConstraint0)) => Option[Project]
  ): LazyList[Project] =
    project.parent0
      .map {
        case (m, v) =>
          (m, VersionConstraint0.fromVersion(v))
      }
      .flatMap(projectCache) match {
      case None         => LazyList.empty
      case Some(parent) => parent #:: parents(parent, projectCache)
    }

  /** Get the dependencies of `project`, knowing that it came from dependency `from` (that is,
    * `from.module == project.module`).
    *
    * Substitute properties, update scopes, apply exclusions, and get extra parameters from
    * dependency management along the way.
    */
  private def finalDependencies(
    from: Dependency,
    project: Project,
    defaultConfiguration: Configuration,
    defaultAttributes: VariantSelector.AttributesBased,
    projectCache: ((Module, VersionConstraint0)) => Option[Project],
    keepProvidedDependencies: Boolean,
    forceDepMgmtVersions: Boolean,
    enableDependencyOverrides: Boolean
  ): Either[DependencyError, Seq[Dependency]] = {

    // section numbers in the comments refer to withDependencyManagement

    val parentProperties0 = parents(project, projectCache)
      .toVector
      .flatMap(_.properties)

    val projectWithProperties = withFinalProperties(
      project.withProperties(parentProperties0 ++ project.properties)
    )

    val actualConfigOrError = finalSelector(
      from,
      if (projectWithProperties.variants.isEmpty)
        Some(projectWithProperties.configurations.keySet)
      else
        None,
      defaultConfiguration,
      defaultAttributes
    )

    val project0 = actualConfigOrError match {
      case Right(attr: VariantSelector.AttributesBased) =>
        projectWithProperties.withDependencies0 {
          projectWithProperties.dependencies0.map {
            case (v: Variant.Attributes, dep) =>
              val variantSelectorOverride = dep.variantSelector match {
                case a: VariantSelector.AttributesBased if a.isEmpty =>
                  Some(attr)
                case _ =>
                  None
              }
              val dep0 = variantSelectorOverride.fold(dep)(dep.withVariantSelector)
              (v, dep0)
            case other => other
          }
        }
      case Right(_: VariantSelector.ConfigurationBased) => projectWithProperties
      case Left(_)                                      => projectWithProperties
    }
    val properties = project0.properties.toMap

    val configurationsOrVariantOrError = actualConfigOrError.flatMap {
      case c: VariantSelector.ConfigurationBased =>
        Right(Right(parentConfigurations(c.configuration, project0.configurations)))
      case attr: VariantSelector.AttributesBased =>
        project0.variantFor(attr).map(Left(_))
    }

    val withProvidedOpt =
      if (keepProvidedDependencies) Some(Configuration.provided)
      else None
    val keepConfigOpt = actualConfigOrError match {
      case Right(c: VariantSelector.ConfigurationBased) =>
        Some {
          (
            c.configuration,
            project0.allConfigurations.get(c.configuration).map(_ ++ withProvidedOpt)
          )
        }
      case Right(_: VariantSelector.AttributesBased) =>
        None
      case Left(_) =>
        None
    }

    val rawDeps = withExclusions0(
      // 2.1 & 2.2
      depsWithDependencyManagement0(
        // 1.7
        project0.dependencies0,
        properties,
        Option.when(enableDependencyOverrides)(from.overridesMap),
        project0.overrides,
        forceDepMgmtVersions = forceDepMgmtVersions,
        keepVariant = keepConfigOpt match {
          case Some((actualConfig, keepConfigOpt0)) =>
            (variant: Variant) =>
              variant.asConfiguration.exists { config =>
                keepConfigOpt0 match {
                  case Some(keep) => keep.contains(config)
                  case None       => config == actualConfig
                }
              }
          case None =>
            // TODO attributes
            (variant: Variant) => false
        }
      ),
      from.minimizedExclusions.toSet()
    )

    configurationsOrVariantOrError.map { configurationsOrVariant0 =>
      rawDeps.flatMap {
        case (variant0, dep0) =>
          // Dependencies from Maven verify
          //   dep.configuration.isEmpty
          // and expect dep.configuration to be filled here

          val dep =
            if (from.optional && !dep0.optional)
              dep0.withOptional(true)
            else
              dep0

          val variant: Variant =
            if (variant0.isEmpty)
              Variant.Configuration(Configuration.compile)
            else
              variant0

          def default = variant match {
            case c: Variant.Configuration =>
              if (configurationsOrVariant0.exists(_.contains(c.configuration))) Seq(dep)
              else Nil
            case a: Variant.Attributes =>
              if (configurationsOrVariant0.left.exists(_ == a)) Seq(dep)
              else Nil
          }

          if (dep.variantSelector.nonEmpty)
            default
          else
            keepConfigOpt match {
              case Some((actualConfig, keepConfigOpt0)) =>
                keepConfigOpt0.fold(default) { keep =>
                  if (variant.asConfiguration.exists(keep)) {
                    val depConfig =
                      if (
                        actualConfig == Configuration.test || actualConfig == Configuration.runtime
                      )
                        Configuration.runtime
                      else
                        defaultConfiguration

                    Seq(dep.withVariantSelector(VariantSelector.ConfigurationBased(depConfig)))
                  }
                  else
                    Nil
                }
              case None =>
                default
            }
      }
    }
  }

  /** Default dependency filter used during resolution.
    *
    * Does not follow optional dependencies.
    */
  def defaultFilter(dep: Dependency): Boolean =
    !dep.optional

  // Same types as sbt, see
  // https://github.com/sbt/sbt/blob/47cd001eea8ef42b7c1db9ffdf48bec16b8f733b/main/src/main/scala/sbt/Defaults.scala#L227
  // https://github.com/sbt/librarymanagement/blob/bb2c73e183fa52e2fb4b9ae7aca55799f3ff6624/ivy/src/main/scala/sbt/internal/librarymanagement/CustomPomParser.scala#L79
  val defaultTypes = Set[Type](
    Type.jar,
    Type.testJar,
    Type.bundle,
    Type.Exotic.mavenPlugin,
    Type.Exotic.eclipsePlugin,
    Type.Exotic.hk2,
    Type.Exotic.orbit,
    Type.Exotic.scalaJar,
    Type.Exotic.klib
  )

  def overrideScalaModule(sv: VersionConstraint0): Dependency => Dependency =
    overrideScalaModule(sv, Organization("org.scala-lang"))

  @deprecated("Use the override accepting a VersionConstraint instead", "2.1.25")
  def overrideScalaModule(sv: String): Dependency => Dependency =
    overrideScalaModule(VersionConstraint0(sv))

  def overrideScalaModule(
    sv: VersionConstraint0,
    scalaOrg: Organization
  ): Dependency => Dependency = {
    val sbv = sv.asString.split('.').take(2).mkString(".")
    val scalaModules =
      if (sbv.startsWith("3"))
        Set(
          ModuleName("scala3-library"),
          ModuleName("scala3-compiler")
        )
      else
        Set(
          ModuleName("scala-library"),
          ModuleName("scala-compiler"),
          ModuleName("scala-reflect"),
          ModuleName("scalap")
        )

    dep =>
      if (dep.module.organization == scalaOrg && scalaModules.contains(dep.module.name))
        dep.withVersionConstraint(sv)
      else
        dep
  }

  @deprecated("Use the override accepting a VersionConstraint instead", "2.1.25")
  def overrideScalaModule(
    sv: String,
    scalaOrg: Organization
  ): Dependency => Dependency =
    overrideScalaModule(
      VersionConstraint0(sv),
      scalaOrg
    )

  /** Replaces the full suffix _2.12.8 with the given Scala version.
    */
  def overrideFullSuffix(sv: String): Dependency => Dependency = {
    val sbv = sv.split('.').take(2).mkString(".")
    def fullCrossVersionBase(module: Module): Option[String] =
      if (module.attributes.isEmpty && !module.name.value.endsWith("_" + sv)) {
        val idx = module.name.value.lastIndexOf("_" + sbv + ".")
        if (idx < 0)
          None
        else {
          val lastPart = module.name.value.substring(idx + 1 + sbv.length + 1)
          if (lastPart.isEmpty || lastPart.exists(c => !"01234566789MRC-.".contains(c)))
            None
          else
            Some(module.name.value.substring(0, idx))
        }
      }
      else
        None

    dep =>
      fullCrossVersionBase(dep.module) match {
        case Some(base) =>
          dep.withModule(dep.module.withName(ModuleName(base + "_" + sv)))
        case None =>
          dep
      }
  }

  @deprecated("Use overrideScalaModule and overrideFullSuffix instead", "2.0.17")
  def forceScalaVersion(sv: String): Dependency => Dependency =
    overrideScalaModule(VersionConstraint0(sv)) andThen overrideFullSuffix(sv)

  private def finalSelector(
    dep: Dependency,
    configsOpt: Option[Set[Configuration]],
    defaultConfig: Configuration,
    defaultAttributes: VariantSelector.AttributesBased
  ): Either[DependencyError, VariantSelector] = {
    def actualConfig(config: Configuration, configs: Set[Configuration]): Configuration =
      actualConfiguration(
        if (config.isEmpty) defaultConfig else config,
        configs
      )
    (dep.variantSelector, configsOpt) match {
      case (c: VariantSelector.ConfigurationBased, Some(configs)) =>
        val actualConfig0 = actualConfig(c.configuration, configs)
        Right {
          if (actualConfig0 == c.configuration)
            c
          else
            VariantSelector.ConfigurationBased(actualConfig0)
        }
      case (c: VariantSelector.ConfigurationBased, None) =>
        c.equivalentAttributesSelector.map(defaultAttributes + _).toRight {
          new VariantError.CannotFindEquivalentVariants(
            dep.module,
            dep.versionConstraint,
            c.configuration
          )
        }
      case (attr: VariantSelector.AttributesBased, Some(configs)) =>
        val config = attr.equivalentConfiguration.getOrElse(
          actualConfig(Configuration.empty, configs)
        )
        Right(VariantSelector.ConfigurationBased(config))
      case (attr: VariantSelector.AttributesBased, None) =>
        Right(defaultAttributes + attr)
    }
  }

  private def fallbackConfigIfNecessary(
    dep: Dependency,
    configsOpt: Option[Set[Configuration]],
    defaultConfiguration: Configuration,
    defaultAttributes: VariantSelector.AttributesBased
  ): Dependency = {
    val updatedSelector = finalSelector(
      dep,
      configsOpt,
      defaultConfiguration,
      defaultAttributes
    ).getOrElse {
      // FIXME Can't convert the attributes to a config, this should be an error
      dep.variantSelector
    }
    if (dep.variantSelector == updatedSelector) dep
    else dep.withVariantSelector(updatedSelector)
  }

  private def withFinalProperties(project: Project): Project =
    project.withProperties(projectProperties(project))

  def enableDependencyOverridesDefault: Boolean = true
}

/** State of a dependency resolution.
  *
  * Done if method `isDone` returns `true`.
  *
  * @param conflicts:
  *   conflicting dependencies
  * @param projectCache0:
  *   cache of known projects
  * @param errorCache:
  *   keeps track of the modules whose project definition could not be found
  */
@data class Resolution(
  rootDependencies: Seq[Dependency] = Nil,
  dependencySet: DependencySet = DependencySet.empty,
  forceVersions0: Map[Module, VersionConstraint0] = Map.empty,
  conflicts: Set[Dependency] = Set.empty,
  projectCache0: Map[Resolution.ModuleVersionConstraint, (ArtifactSource, Project)] = Map.empty,
  errorCache: Map[Resolution.ModuleVersionConstraint, Seq[String]] = Map.empty,
  finalDependenciesCache: Map[Dependency, Seq[Dependency]] = Map.empty,
  filter: Option[Dependency => Boolean] = None,
  reconciliation0: Option[Module => ConstraintReconciliation] = None,
  osInfo: Activation.Os = Activation.Os.empty,
  jdkVersion0: Option[Version0] = None,
  userActivations: Option[Map[String, Boolean]] = None,
  mapDependencies: Option[Dependency => Dependency] = None,
  extraProperties: Seq[(String, String)] = Nil,
  forceProperties: Map[String, String] = Map.empty, // FIXME Make that a seq too?
  defaultConfiguration: Configuration = Configuration.defaultRuntime,
  @since("2.1.9")
  keepProvidedDependencies: Boolean = false,
  @since("2.1.17")
  forceDepMgmtVersions: Boolean = false,
  enableDependencyOverrides: Boolean = Resolution.enableDependencyOverridesDefault,
  @deprecated("Use boms instead", "2.1.18")
  bomDependencies: Seq[Dependency] = Nil,
  @since("2.1.18")
  @deprecated("Use boms instead", "2.1.19")
  bomModuleVersions: Seq[(Module, String)] = Nil,
  @since("2.1.19")
  boms: Seq[BomDependency] = Nil,
  @since("2.1.25")
  defaultVariantAttributes: VariantSelector.AttributesBased =
    VariantSelector.AttributesBased.empty
) {

  lazy val dependencies: Set[Dependency] =
    dependencySet.set

  override lazy val hashCode: Int = super.hashCode

  @deprecated("Use forceVersions0 instead", "2.1.25")
  def forceVersions: Map[Module, String] =
    forceVersions0.map {
      case (mod, ver) =>
        (mod, ver.asString)
    }
  @deprecated("Use withForceVersions0 instead", "2.1.25")
  def withForceVersions(newForceVersions: Map[Module, String]): Resolution =
    withForceVersions0(
      newForceVersions.map {
        case (mod, ver) =>
          (mod, VersionConstraint0(ver))
      }
    )

  @deprecated("Use projectCache0 instead", "2.1.25")
  def projectCache: Map[(Module, String), (ArtifactSource, Project)] =
    projectCache0.map {
      case ((mod, ver), value) =>
        ((mod, ver.asString), value)
    }
  @deprecated("Use withProjectCache0 instead", "2.1.25")
  def withProjectCache(newProjectCache: Map[(Module, String), (ArtifactSource, Project)])
    : Resolution =
    withProjectCache0(
      newProjectCache.map {
        case ((mod, ver), value) =>
          ((mod, VersionConstraint0(ver)), value)
      }
    )

  @deprecated("Use reconciliation0 instead", "2.1.25")
  def reconciliation: Option[Module => Reconciliation] =
    reconciliation0.map { f => mod =>
      f(mod) match {
        case ConstraintReconciliation.Default => Reconciliation.Default
        case ConstraintReconciliation.Relaxed => Reconciliation.Relaxed
        case ConstraintReconciliation.Strict  => Reconciliation.Strict
        case ConstraintReconciliation.SemVer  => Reconciliation.SemVer
        case _                                => sys.error("Cannot happen")
      }
    }
  @deprecated("Use withReconciliation0 instead", "2.1.25")
  def withReconciliation(newReconciliation: Option[Module => Reconciliation]): Resolution =
    withReconciliation0(
      newReconciliation.map { f => mod =>
        ConstraintReconciliation(f(mod).id).getOrElse {
          sys.error("Cannot happen")
        }
      }
    )

  @deprecated("Use jdkVersion0 instead", "2.1.25")
  def jdkVersion: Option[String] =
    jdkVersion0.map(_.asString)
  @deprecated("Use withJdkVersion0 instead", "2.1.25")
  def withJdkVersion(newJdkVersion: Option[String]): Resolution =
    withJdkVersion0(newJdkVersion.map(Version0(_)))

  def withDependencies(dependencies: Set[Dependency]): Resolution =
    withDependencySet(dependencySet.setValues(dependencies))

  def addToErrorCache0(entries: Iterable[(Resolution.ModuleVersionConstraint, Seq[String])])
    : Resolution =
    copyWithCache(
      errorCache = errorCache ++ entries
    )

  @deprecated("Use addToErrorCache0 instead", "2.1.25")
  def addToErrorCache(entries: Iterable[((Module, String), Seq[String])])
    : Resolution =
    addToErrorCache0(
      entries.map {
        case ((mod, ver), value) =>
          ((mod, VersionConstraint0(ver)), value)
      }
    )

  private def copyWithCache(
    rootDependencies: Seq[Dependency] = rootDependencies,
    dependencySet: DependencySet = dependencySet,
    conflicts: Set[Dependency] = conflicts,
    errorCache: Map[Resolution.ModuleVersionConstraint, Seq[String]] = errorCache
    // don't allow changing mapDependencies here - that would invalidate finalDependenciesCache
    // don't allow changing projectCache0 here - use addToProjectCache0 that takes forceProperties into account
  ): Resolution =
    withRootDependencies(rootDependencies)
      .withDependencySet(dependencySet)
      .withConflicts(conflicts)
      .withErrorCache(errorCache)
      .withFinalDependenciesCache(finalDependenciesCache ++ finalDependenciesCache0.asScala)

  def addToProjectCache0(
    projects: (Resolution.ModuleVersionConstraint, (ArtifactSource, Project))*
  ): Resolution = {

    val duplicates = projects
      .collect {
        case (modVer, _) if projectCache0.contains(modVer) =>
          modVer
      }

    assert(
      duplicates.isEmpty,
      s"Projects already added in resolution: ${duplicates.mkString(", ")}"
    )

    withFinalDependenciesCache(finalDependenciesCache ++ finalDependenciesCache0.asScala)
      .withProjectCache0 {
        projectCache0 ++ projects.map {
          case (modVer, (s, p)) =>
            val p0 =
              withDependencyManagement(
                p.withProperties(
                  extraProperties ++
                    p.properties.filter(kv => !forceProperties.contains(kv._1)) ++
                    forceProperties
                )
              )
            (modVer, (s, p0))
        }
      }
  }

  @deprecated("Use addToProjectCache0 instead", "2.1.25")
  def addToProjectCache(
    projects: ((Module, String), (ArtifactSource, Project))*
  ): Resolution =
    addToProjectCache0(
      projects.map {
        case ((mod, ver), value) =>
          ((mod, VersionConstraint0(ver)), value)
      }: _*
    )

  import Resolution._

  private[core] val finalDependenciesCache0 = new ConcurrentHashMap[Dependency, Seq[Dependency]]

  private def finalDependencies0(dep: Dependency): Either[DependencyError, Seq[Dependency]] =
    if (dep.transitive) {
      val deps = finalDependenciesCache.getOrElse(dep, finalDependenciesCache0.get(dep))

      if (deps == null)
        projectCache0.get(dep.moduleVersionConstraint) match {
          case Some((_, proj)) =>
            val resOrError = finalDependencies(
              dep,
              proj,
              defaultConfiguration,
              defaultVariantAttributes,
              k => projectCache0.get(k).map(_._2),
              keepProvidedDependencies,
              forceDepMgmtVersions,
              enableDependencyOverrides
            ).map(_.filter(filter getOrElse defaultFilter))
              .map(res0 => mapDependencies.fold(res0)(res0.map(_)))
            for (res <- resOrError)
              finalDependenciesCache0.put(dep, res)
            resOrError
          case None => Right(Nil)
        }
      else
        Right(deps)
    }
    else
      Right(Nil)

  def dependenciesOf0(dep: Dependency): Either[DependencyError, Seq[Dependency]] =
    dependenciesOf0(dep, withRetainedVersions = false)

  @deprecated("Use dependenciesOf0 instead", "2.1.25")
  def dependenciesOf(dep: Dependency): Seq[Dependency] =
    dependenciesOf0(dep).toTry.get

  def dependenciesOf0(
    dep: Dependency,
    withRetainedVersions: Boolean
  ): Either[DependencyError, Seq[Dependency]] =
    dependenciesOf0(
      dep,
      withRetainedVersions = withRetainedVersions,
      withReconciledVersions = false,
      withFallbackConfig = false
    )

  @deprecated("Use dependenciesOf0 instead", "2.1.25")
  def dependenciesOf(
    dep: Dependency,
    withRetainedVersions: Boolean
  ): Seq[Dependency] =
    dependenciesOf0(dep, withRetainedVersions).toTry.get

  private def configsOf(dep: Dependency): Option[Set[Configuration]] = {
    val reconciledVersion = reconciledVersions.getOrElse(
      dep.module,
      sys.error(s"No reconciled version found for ${dep.module}")
    )
    projectCache0
      .get((dep.module, reconciledVersion))
      .map(_._2)
      .map { proj =>
        if (proj.variants.isEmpty)
          Some(proj.configurations.keySet)
        else
          None
      }
      .getOrElse(Some(Set.empty))
  }

  private def updated(
    dep: Dependency,
    withRetainedVersions: Boolean,
    withReconciledVersions: Boolean,
    withFallbackConfig: Boolean,
    loose: Boolean = false
  ): Dependency = {
    var dep0 = dep
    if (withRetainedVersions)
      dep0 = dep0.withVersionConstraint(
        (if (loose) retainedVersionsLoose else retainedVersions)
          .get(dep0.module)
          .map(v => VersionConstraint0.fromVersion(v))
          .getOrElse(dep0.versionConstraint)
      )
    else if (withReconciledVersions)
      dep0 = dep0.withVersionConstraint(
        reconciledVersions
          .get(dep0.module)
          .getOrElse(dep0.versionConstraint)
      )
    if (withFallbackConfig)
      dep0 = Resolution.fallbackConfigIfNecessary(
        dep0,
        configsOf(dep0),
        defaultConfiguration,
        defaultVariantAttributes
      )
    dep0
  }

  def dependenciesOf0(
    dep: Dependency,
    withRetainedVersions: Boolean,
    withReconciledVersions: Boolean,
    withFallbackConfig: Boolean
  ): Either[DependencyError, Seq[Dependency]] =
    finalDependencies0(dep).map { deps =>
      if (withRetainedVersions || withFallbackConfig)
        deps.map(updated(_, withRetainedVersions, withReconciledVersions, withFallbackConfig))
      else
        deps
    }

  @deprecated("Use dependenciesOf0 instead", "2.1.25")
  def dependenciesOf(
    dep: Dependency,
    withRetainedVersions: Boolean,
    withReconciledVersions: Boolean,
    withFallbackConfig: Boolean
  ): Seq[Dependency] =
    dependenciesOf0(
      dep,
      withRetainedVersions,
      withReconciledVersions,
      withFallbackConfig
    ).toTry.get

  def dependenciesOf0(
    dep: Dependency,
    withRetainedVersions: Boolean,
    withFallbackConfig: Boolean
  ): Either[DependencyError, Seq[Dependency]] =
    dependenciesOf0(
      dep,
      withRetainedVersions,
      withReconciledVersions = !withRetainedVersions,
      withFallbackConfig
    )

  @deprecated("Use dependenciesOf0 instead", "2.1.25")
  def dependenciesOf(
    dep: Dependency,
    withRetainedVersions: Boolean,
    withFallbackConfig: Boolean
  ): Seq[Dependency] =
    dependenciesOf0(
      dep,
      withRetainedVersions,
      withFallbackConfig
    ).toTry.get

  /** Transitive dependencies of the current dependencies, according to what there currently is in
    * cache.
    *
    * No attempt is made to solve version conflicts here.
    */
  lazy val transitiveDependenciesAndErrors
    : (Seq[(Dependency, DependencyError)], Seq[Dependency]) = {
    val l = (dependencySet.minimizedSet -- conflicts)
      .toVector
      .map(dep => finalDependencies0(dep).left.map((dep, _)))
    val errors = l.collect {
      case Left(depAndError) => depAndError
    }
    val deps = l.flatMap(_.toSeq).flatten
    (errors, deps)
  }

  def transitiveDependencies: Seq[Dependency] = transitiveDependenciesAndErrors._2

  private lazy val globalBomModuleVersions =
    ResolutionInternals.deprecatedBomDependencies(this)
      .map(_.moduleVersionConstraint)
      .map { case (m, v) =>
        BomDependency(m, v, defaultConfiguration)
      } ++
      ResolutionInternals.deprecatedBomModuleVersions(this)
        .map { case (m, v) => BomDependency(m, VersionConstraint0(v), defaultConfiguration) } ++
      boms
  private lazy val allBomModuleVersions =
    globalBomModuleVersions ++ rootDependencies.flatMap(_.bomDependencies)
  private def bomEntries(bomDeps: Seq[BomDependency]): Overrides =
    Overrides.add {
      (for {
        bomDep          <- bomDeps
        (_, bomProject) <- projectCache0.get(bomDep.moduleVersionConstraint).toSeq
        bomConfig0 = actualConfiguration(
          if (bomDep.config.isEmpty) defaultConfiguration
          else bomDep.config,
          bomProject.configurations
        )
        // adding the initial config too in case it's the "provided" config
        keepConfigs = bomProject.allConfigurations.getOrElse(bomConfig0, Set()) + bomConfig0
      } yield {
        val retainedEntries = bomProject.overrides.filter {
          (k, v) =>
            v.config.isEmpty || keepConfigs.contains(v.config)
        }
        withProperties(retainedEntries, projectProperties(bomProject).toMap)
      }): _*
    }
  lazy val bomDepMgmtOverrides = bomEntries(globalBomModuleVersions)
  @deprecated("Use bomDepMgmtOverrides.flatten instead", "2.1.23")
  def bomDepMgmt = bomDepMgmtOverrides.flatten.toMap
  lazy val hasAllBoms =
    allBomModuleVersions.forall { bomDep =>
      projectCache0.contains(bomDep.moduleVersionConstraint)
    }
  lazy val processedRootDependencies =
    if (hasAllBoms) {
      val rootDependenciesWithDefaultConfig =
        rootDependencies.map(withDefaultConfig(_, defaultConfiguration))
      val rootDependencies0 =
        if (bomDepMgmtOverrides.isEmpty) rootDependenciesWithDefaultConfig
        else
          rootDependenciesWithDefaultConfig.map { rootDep =>
            val rootDep0 = rootDep.addOverrides(bomDepMgmtOverrides)
            if (rootDep0.versionConstraint.asString.isEmpty)
              bomDepMgmtOverrides.get(DependencyManagement.Key.from(rootDep0)) match {
                case Some(values) => rootDep0.withVersionConstraint(values.versionConstraint)
                case None         => rootDep0
              }
            else
              rootDep0
          }
      rootDependencies0.map { rootDep =>
        val depBomDepMgmt = bomEntries(rootDep.bomDependencies)
        val overrideDepBomDepMgmt =
          bomEntries(rootDep.bomDependencies.filter(_.forceOverrideVersions))
        val rootDep0 = rootDep.addOverrides(depBomDepMgmt)
        val key      = DependencyManagement.Key.from(rootDep0)
        overrideDepBomDepMgmt.get(key) match {
          case Some(overrideValues) =>
            rootDep0.withVersionConstraint(overrideValues.versionConstraint)
          case None =>
            if (rootDep0.versionConstraint.asString.isEmpty)
              depBomDepMgmt.get(key) match {
                case Some(values) => rootDep0.withVersionConstraint(values.versionConstraint)
                case None         => rootDep0
              }
            else
              rootDep0
        }
      }
    }
    else
      Nil

  /** The "next" dependency set, made of the current dependencies and their transitive dependencies,
    * trying to solve version conflicts. Transitive dependencies are calculated with the current
    * cache.
    *
    * May contain dependencies added in previous iterations, but no more required. These are
    * filtered below, see `newDependencies`.
    *
    * Returns a tuple made of the conflicting dependencies, all the dependencies, and the retained
    * version of each module.
    */
  lazy val nextDependenciesAndConflicts
    : (Seq[Dependency], Seq[Dependency], Map[Module, VersionConstraint0]) =
    // TODO Provide the modules whose version was forced by dependency overrides too
    merge0(
      processedRootDependencies ++ transitiveDependencies,
      forceVersions0,
      reconciliation0
    )

  lazy val retainedVersions: Map[Module, Version0] =
    nextDependenciesAndConflicts._3.map {
      case k @ (m, v) =>
        projectCache0.get(k)
          .map {
            case (_, proj) =>
              m -> proj.actualVersion0
          }
          .getOrElse {
            sys.error(s"Cannot find $m:${v.asString} in projectCache")
          }
    }

  // same as retainedVersions, but doesn't throw if a module isn't found
  // useful for failed resolutions
  private lazy val retainedVersionsLoose: Map[Module, Version0] =
    nextDependenciesAndConflicts._3.map {
      case k @ (m, v) =>
        val finalVersion = projectCache0.get(k)
          .map {
            case (_, proj) =>
              proj.actualVersion0
          }
          .getOrElse {
            Version0(v.generateString)
          }
        m -> finalVersion
    }

  def reconciledVersions: Map[Module, VersionConstraint0] =
    nextDependenciesAndConflicts._3

  /** The modules we miss some info about.
    */
  lazy val missingFromCache: Set[ModuleVersionConstraint] = {
    val boms = allBomModuleVersions
      .map(_.moduleVersionConstraint)
      .toSet
    val modules = dependencies
      .map(_.moduleVersionConstraint)
    val nextModules = nextDependenciesAndConflicts._2
      .map(_.moduleVersionConstraint)

    (boms ++ modules ++ nextModules)
      .filterNot(mod => projectCache0.contains(mod) || errorCache.contains(mod))
  }

  /** Whether the resolution is done.
    */
  lazy val isDone: Boolean = {
    def isFixPoint = {
      val (nextConflicts, _, _) = nextDependenciesAndConflicts

      dependencies == (newDependencies ++ nextConflicts) &&
      conflicts == nextConflicts.toSet
    }

    missingFromCache.isEmpty && isFixPoint
  }

  /** Returns a map giving the dependencies that brought each of the dependency of the "next"
    * dependency set.
    *
    * The versions of all the dependencies returned are erased (emptied).
    */
  lazy val reverseDependencies: Map[Dependency, Vector[Dependency]] = {
    val (updatedConflicts, updatedDeps, _) = nextDependenciesAndConflicts

    val trDepsSeq =
      for {
        dep   <- updatedDeps
        trDep <- finalDependencies0(dep).toOption.getOrElse(Nil)
      } yield trDep.clearVersion -> dep.clearVersion

    val knownDeps = (updatedDeps ++ updatedConflicts)
      .map(_.clearVersion)
      .toSet

    trDepsSeq
      .groupBy(_._1)
      .view
      .mapValues(_.map(_._2).toVector)
      .filterKeys(knownDeps)
      .toMap // Eagerly evaluate filterKeys/mapValues
  }

  /** Returns dependencies from the "next" dependency set, filtering out those that are no more
    * required.
    *
    * The versions of all the dependencies returned are erased (emptied).
    */
  lazy val remainingDependencies: Set[Dependency] = {
    val rootDependencies0 = processedRootDependencies.map(_.clearVersion).toSet

    @tailrec
    def helper(
      reverseDeps: Map[Dependency, Vector[Dependency]]
    ): Map[Dependency, Vector[Dependency]] = {

      val (toRemove, remaining) = reverseDeps
        .partition(kv => kv._2.isEmpty && !rootDependencies0(kv._1))

      if (toRemove.isEmpty)
        reverseDeps
      else
        helper(
          remaining
            .view
            .mapValues(broughtBy =>
              broughtBy
                .filter(x => remaining.contains(x) || rootDependencies0(x))
            )
            .iterator
            .toMap
        )
    }

    val filteredReverseDependencies = helper(reverseDependencies)

    rootDependencies0 ++ filteredReverseDependencies.keys
  }

  /** The final next dependency set, stripped of no more required ones.
    */
  lazy val newDependencies: Set[Dependency] = {
    val remainingDependencies0 = remainingDependencies

    nextDependenciesAndConflicts._2
      .filter(dep => remainingDependencies0(dep.clearVersion))
      .toSet
  }

  private lazy val nextNoMissingUnsafe: Resolution = {
    val (newConflicts, _, _) = nextDependenciesAndConflicts

    copyWithCache(
      dependencySet = dependencySet.setValues(newDependencies ++ newConflicts),
      conflicts = newConflicts.toSet
    )
  }

  /** If no module info is missing, the next state of the resolution, which can be immediately
    * calculated. Else, the current resolution.
    */
  @tailrec
  final def nextIfNoMissing: Resolution = {
    val missing = missingFromCache

    if (missing.isEmpty) {
      val next0 = nextNoMissingUnsafe

      if (next0 == this)
        this
      else
        next0.nextIfNoMissing
    }
    else
      this
  }

  /** Required modules for the dependency management of `project`.
    */
  def dependencyManagementRequirements0(
    project: Project
  ): Set[ModuleVersionConstraint] = {

    val needsParent =
      project.parent0.exists {
        case (parMod, parVer) =>
          val par0        = (parMod, VersionConstraint0.fromVersion(parVer))
          val parentFound = projectCache0.contains(par0) || errorCache.contains(par0)
          !parentFound
      }

    if (needsParent)
      project.parent0
        .map {
          case (parMod, parVer) =>
            (parMod, VersionConstraint0.fromVersion(parVer))
        }
        .toSet
    else {

      val parentProperties0 = parents(project, k => projectCache0.get(k).map(_._2))
        .toVector
        .flatMap(_.properties)

      // 1.1 (see above)
      val approxProperties = parentProperties0.toMap ++ projectProperties(project)

      val profiles = profiles0(
        project,
        approxProperties,
        osInfo,
        jdkVersion0,
        userActivations
      )

      val profileDependencies = profiles.flatMap(p => p.dependencies ++ p.dependencyManagement)

      val project0 =
        project.withProperties(
          project.properties ++ profiles.flatMap(_.properties)
        ) // belongs to 1.5 & 1.6

      val propertiesMap0 = withFinalProperties(
        project0.withProperties(parentProperties0 ++ project0.properties)
      ).properties.toMap

      val modules = withProperties0(
        project0.dependencies0 ++
          project0.dependencyManagement.map { case (c, d) => (Variant.Configuration(c), d) } ++
          profileDependencies.map { case (c, d) => (Variant.Configuration(c), d) },
        propertiesMap0
      ).collect {
        case (v: Variant.Configuration, dep) if v.configuration == Configuration.`import` =>
          dep.moduleVersionConstraint
      }

      modules.toSet
    }
  }

  @deprecated("Use dependencyManagementRequirements0 instead", "2.1.25")
  def dependencyManagementRequirements(
    project: Project
  ): Set[(Module, String)] =
    dependencyManagementRequirements0(project).map {
      case (mod, ver) =>
        (mod, ver.asString)
    }

  /** Missing modules in cache, to get the full list of dependencies of `project`, taking dependency
    * management / inheritance into account.
    *
    * Note that adding the missing modules to the cache may unveil other missing modules, so these
    * modules should be added to the cache, and `dependencyManagementMissing0` checked again for new
    * missing modules.
    */
  def dependencyManagementMissing0(project: Project): Set[ModuleVersionConstraint] = {

    @tailrec
    def helper(
      toCheck: Set[ModuleVersionConstraint],
      done: Set[ModuleVersionConstraint],
      missing: Set[ModuleVersionConstraint]
    ): Set[ModuleVersionConstraint] =
      if (toCheck.isEmpty)
        missing
      else if (toCheck.exists(done))
        helper(toCheck -- done, done, missing)
      else if (toCheck.exists(missing))
        helper(toCheck -- missing, done, missing)
      else if (toCheck.exists(projectCache0.contains)) {
        val (checking, remaining) = toCheck.partition(projectCache0.contains)
        val directRequirements = checking
          .flatMap(mod => dependencyManagementRequirements0(projectCache0(mod)._2))

        helper(remaining ++ directRequirements, done ++ checking, missing)
      }
      else if (toCheck.exists(errorCache.contains)) {
        val (errored, remaining) = toCheck.partition(errorCache.contains)
        helper(remaining, done ++ errored, missing)
      }
      else
        helper(Set.empty, done, missing ++ toCheck)

    helper(
      dependencyManagementRequirements0(project),
      Set((project.module, VersionConstraint0.fromVersion(project.version0))),
      Set.empty
    )
  }

  @deprecated("Use dependencyManagementMissing0 instead", "2.1.25")
  def dependencyManagementMissing(project: Project): Set[(Module, String)] =
    dependencyManagementMissing0(project).map {
      case (mod, ver) =>
        (mod, ver.asString)
    }

  /** Add dependency management / inheritance related items to `project`, from what's available in
    * cache.
    *
    * It is recommended to have fetched what `dependencyManagementMissing0` returned prior to
    * calling this.
    */
  def withDependencyManagement(project: Project): Project = {

    /*

       Loosely following what [Maven says](http://maven.apache.org/components/ref/3.3.9/maven-model-builder/):
       (thanks to @MasseGuillaume for pointing that doc out)

    phase 1
         1.1 profile activation: see available activators. Notice that model interpolation hasn't happened yet, then interpolation for file-based activation is limited to ${basedir} (since Maven 3), System properties and request properties
         1.2 raw model validation: ModelValidator (javadoc), with its DefaultModelValidator implementation (source)
         1.3 model normalization - merge duplicates: ModelNormalizer (javadoc), with its DefaultModelNormalizer implementation (source)
         1.4 profile injection: ProfileInjector (javadoc), with its DefaultProfileInjector implementation (source)
         1.5 parent resolution until super-pom
         1.6 inheritance assembly: InheritanceAssembler (javadoc), with its DefaultInheritanceAssembler implementation (source). Notice that project.url, project.scm.connection, project.scm.developerConnection, project.scm.url and project.distributionManagement.site.url have a special treatment: if not overridden in child, the default value is parent's one with child artifact id appended
         1.7 model interpolation (see below)
     N/A     url normalization: UrlNormalizer (javadoc), with its DefaultUrlNormalizer implementation (source)
    phase 2, with optional plugin processing
     N/A     model path translation: ModelPathTranslator (javadoc), with its DefaultModelPathTranslator implementation (source)
     N/A     plugin management injection: PluginManagementInjector (javadoc), with its DefaultPluginManagementInjector implementation (source)
     N/A     (optional) lifecycle bindings injection: LifecycleBindingsInjector (javadoc), with its DefaultLifecycleBindingsInjector implementation (source)
         2.1 dependency management import (for dependencies of type pom in the <dependencyManagement> section)
         2.2 dependency management injection: DependencyManagementInjector (javadoc), with its DefaultDependencyManagementInjector implementation (source)
         2.3 model normalization - inject default values: ModelNormalizer (javadoc), with its DefaultModelNormalizer implementation (source)
     N/A     (optional) reports configuration: ReportConfigurationExpander (javadoc), with its DefaultReportConfigurationExpander implementation (source)
     N/A     (optional) reports conversion to decoupled site plugin: ReportingConverter (javadoc), with its DefaultReportingConverter implementation (source)
     N/A     (optional) plugins configuration: PluginConfigurationExpander (javadoc), with its DefaultPluginConfigurationExpander implementation (source)
         2.4 effective model validation: ModelValidator (javadoc), with its DefaultModelValidator implementation (source)

    N/A: does not apply here (related to plugins, path of project being built, ...)

     */

    // A bit fragile, but seems to work

    val parentProperties0 = parents(project, k => projectCache0.get(k).map(_._2))
      .toVector
      .flatMap(_.properties)

    // 1.1 (see above)
    val approxProperties = parentProperties0.toMap ++ projectProperties(project)

    val profiles = profiles0(
      project,
      approxProperties,
      osInfo,
      jdkVersion0,
      userActivations
    )

    // 1.2 made from Pom.scala (TODO look at the very details?)

    // 1.3 & 1.4 (if only vaguely so)
    val project0 =
      project.withProperties(
        project.properties ++ profiles.flatMap(_.properties)
      ) // belongs to 1.5 & 1.6

    val propertiesMap0 = withFinalProperties(
      project0.withProperties(parentProperties0 ++ project0.properties)
    ).properties.toMap

    val (importDeps, standardDeps) = {

      val dependencies0 = addDependencies(
        project0.dependencies0 +:
          profiles.map(_.dependencies.map { case (c, d) => (Variant.Configuration(c), d) })
      )

      val (importDeps0, standardDeps0) = dependencies0
        .map { dep =>
          val dep0 = withProperties(dep, propertiesMap0)
          if (dep0._1.asConfiguration.exists(_ == Configuration.`import`))
            (dep0._2 :: Nil, Nil)
          else
            (Nil, dep :: Nil) // not dep0 (properties with be substituted later)
        }
        .unzip

      (importDeps0.flatten, standardDeps0.flatten)
    }

    val importDepsMgmt = {

      val dependenciesMgmt0 = addDependencies(
        (project0.dependencyManagement +: profiles.map(_.dependencyManagement))
          .map(_.map { case (c, d) => (Variant.Configuration(c), d) })
      )

      dependenciesMgmt0.flatMap { dep =>
        val (conf0, dep0) = withProperties(dep, propertiesMap0)
        if (conf0.asConfiguration.exists(_ == Configuration.`import`))
          dep0 :: Nil
        else
          Nil
      }
    }

    val parentDeps = project0.parent0
      .map {
        case (parMod, parVer) =>
          (parMod, VersionConstraint0.fromVersion(parVer))
      }
      .toSeq // belongs to 1.5 & 1.6

    val allImportDeps =
      importDeps.map(_.moduleVersionConstraint) ++
        importDepsMgmt.map(_.moduleVersionConstraint)

    val retainedParentDeps = parentDeps.filter(projectCache0.contains)
    val retainedImportDeps = allImportDeps.filter(projectCache0.contains)

    val retainedParentProjects = retainedParentDeps.map(projectCache0(_)._2)
    val retainedImportProjects = retainedImportDeps.map(projectCache0(_)._2)

    val depMgmtInputs =
      (project0.dependencyManagement +: profiles.map(_.dependencyManagement))
        .map(_.filter(_._1 != Configuration.`import`))
        .filter(_.nonEmpty)
        .map { input =>
          Overrides(
            DependencyManagement.addDependencies(
              Map.empty,
              input,
              composeValues = enableDependencyOverrides
            )
          )
        }

    val depMgmt = Overrides.add(
      // our dep management
      // takes precedence over dep imports
      // that takes precedence over parents
      depMgmtInputs ++
        retainedImportProjects.map { p =>
          withProperties(p.overrides, projectProperties(p).toMap)
        } ++
        retainedParentProjects.map { p =>
          withProperties(p.overrides, staticProjectProperties(p).toMap)
        }: _*
    )

    project0
      .withPackagingOpt(project0.packagingOpt.map(_.map(substituteProps(_, propertiesMap0))))
      .withVersion0(Version0(substituteProps(project0.version0.asString, propertiesMap0)))
      .withDependencies0(
        standardDeps ++
          project0.parent0 // belongs to 1.5 & 1.6
            .map {
              case (parMod, parVer) =>
                (parMod, VersionConstraint0.fromVersion(parVer))
            }
            .filter(projectCache0.contains)
            .toSeq
            .flatMap(projectCache0(_)._2.dependencies0)
      )
      .withDependencyManagement(Nil)
      .withOverrides(depMgmt)
      .withProperties(
        retainedParentProjects.flatMap(_.properties) ++ project0.properties
      )
  }

  /** Minimized dependency set. Returns `dependencies` with no redundancy.
    *
    * E.g. `dependencies` may contains several dependencies towards module org:name:version, a first
    * one excluding A and B, and a second one excluding A and C. In practice, B and C will be
    * brought anyway, because the first dependency doesn't exclude C, and the second one doesn't
    * exclude B. So having both dependencies is equivalent to having only one dependency towards
    * org:name:version, excluding just A.
    *
    * The same kind of substitution / filtering out can be applied with configurations. If
    * `dependencies` contains several dependencies towards org:name:version, a first one bringing
    * its configuration "runtime", a second one "compile", and the configuration mapping of
    * org:name:version says that "runtime" extends "compile", then all the dependencies brought by
    * the latter will be brought anyway by the former, so that the latter can be removed.
    *
    * @return
    *   A minimized `dependencies`, applying this kind of substitutions.
    */
  def minDependencies: Set[Dependency] =
    minimizedDependencies(
      withRetainedVersions = true,
      withReconciledVersions = false,
      withFallbackConfig = true
    )

  def minimizedDependencies(
    withRetainedVersions: Boolean = true,
    withReconciledVersions: Boolean = false,
    withFallbackConfig: Boolean = true
  ): Set[Dependency] =
    dependencySet.minimizedSet.map { dep =>
      updated(
        dep,
        withRetainedVersions = withRetainedVersions,
        withReconciledVersions = withReconciledVersions,
        withFallbackConfig = withFallbackConfig
      )
    }

  /** Same as minimizedDependencies, but doesn't throw upon internal inconsistency
    *
    * Such inconsistencies can happen with failed resolutions in particular.
    *
    * @param withRetainedVersions
    * @param withReconciledVersions
    * @param withFallbackConfig
    * @return
    */
  def minimizedDependenciesLoose(
    withRetainedVersions: Boolean = true,
    withReconciledVersions: Boolean = false,
    withFallbackConfig: Boolean = true
  ): Set[Dependency] =
    dependencySet.minimizedSet.map { dep =>
      updated(
        dep,
        withRetainedVersions = withRetainedVersions,
        withReconciledVersions = withReconciledVersions,
        withFallbackConfig = withFallbackConfig,
        loose = true
      )
    }

  private def orderedDependencies0(
    keepOverrides: Boolean,
    keepReconciledVersions: Boolean
  ): Seq[Dependency] = {

    def helper(deps: List[Dependency], done: DependencySet): LazyList[Dependency] =
      deps match {
        case Nil => LazyList.empty
        case h :: t =>
          val h0 = if (keepOverrides) h else h.clearOverrides
          if (done.covers(h0))
            helper(t, done)
          else {
            lazy val done0 = done.add(h0)
            val todo = dependenciesOf0(
              h,
              withRetainedVersions = false,
              withReconciledVersions = true,
              withFallbackConfig = true
            )
              .toOption
              .getOrElse(Nil)
              // filtering with done0 rather than done for some cycles (dependencies having themselves as dependency)
              .filter(!done0.covers(_))
            val t0 =
              if (todo.isEmpty) t
              else t ::: todo.toList
            h0 #:: helper(t0, done0)
          }
      }

    val (_, updatedRootDependencies, _) = merge0(
      processedRootDependencies,
      forceVersions0,
      reconciliation0,
      preserveOrder = true
    )
    val rootDeps = updatedRootDependencies
      .map(withDefaultConfig(_, defaultConfiguration))
      .map(dep =>
        updated(
          dep,
          withRetainedVersions = false,
          withReconciledVersions = true,
          withFallbackConfig = true
        )
      )
      .toList

    helper(rootDeps, DependencySet.empty)
      .map { dep =>
        updated(
          dep,
          withRetainedVersions = !keepReconciledVersions,
          withReconciledVersions = keepReconciledVersions,
          withFallbackConfig = false
        )
      }
      .toVector
  }

  def orderedDependencies: Seq[Dependency] =
    orderedDependencies(keepReconciledVersions = false)
  def orderedDependenciesWithOverrides: Seq[Dependency] =
    orderedDependenciesWithOverrides(keepReconciledVersions = false)

  def orderedDependencies(keepReconciledVersions: Boolean): Seq[Dependency] =
    orderedDependencies0(keepOverrides = false, keepReconciledVersions = keepReconciledVersions)
  def orderedDependenciesWithOverrides(keepReconciledVersions: Boolean): Seq[Dependency] =
    orderedDependencies0(keepOverrides = true, keepReconciledVersions = keepReconciledVersions)

  def artifacts(): Seq[Artifact] =
    artifacts(defaultTypes, None)
  def artifacts(types: Set[Type]): Seq[Artifact] =
    artifacts(types, None)
  def artifacts(classifiers: Option[Seq[Classifier]]): Seq[Artifact] =
    artifacts(defaultTypes, classifiers)

  def artifacts(types: Set[Type], classifiers: Option[Seq[Classifier]]): Seq[Artifact] =
    artifacts(types, classifiers, classpathOrder = true)

  def artifacts(
    types: Set[Type],
    classifiers: Option[Seq[Classifier]],
    classpathOrder: Boolean
  ): Seq[Artifact] =
    dependencyArtifacts0(classifiers)
      .collect {
        case (_, Right(pub), artifact) if types(pub.`type`) =>
          artifact
      }
      .distinct

  @deprecated("Use dependencyArtifacts0 instead", "2.1.25")
  def dependencyArtifacts(): Seq[(Dependency, Publication, Artifact)] =
    dependencyArtifacts(None)

  @deprecated("Use dependencyArtifacts0 instead", "2.1.25")
  def dependencyArtifacts(
    classifiers: Option[Seq[Classifier]]
  ): Seq[(Dependency, Publication, Artifact)] =
    dependencyArtifacts(classifiers, classpathOrder = true)

  @deprecated("Use dependencyArtifacts0 instead", "2.1.25")
  def dependencyArtifacts(
    classifiers: Option[Seq[Classifier]],
    classpathOrder: Boolean
  ): Seq[(Dependency, Publication, Artifact)] =
    dependencyArtifacts0(classifiers, classpathOrder).collect {
      case (dep, Right(pub), art) =>
        (dep, pub, art)
    }

  def dependencyArtifacts0(): Seq[(Dependency, Either[VariantPublication, Publication], Artifact)] =
    dependencyArtifacts0(None)

  def dependencyArtifacts0(
    classifiers: Option[Seq[Classifier]]
  ): Seq[(Dependency, Either[VariantPublication, Publication], Artifact)] =
    dependencyArtifacts0(classifiers, classpathOrder = true)

  def dependencyArtifacts0(
    classifiers: Option[Seq[Classifier]],
    classpathOrder: Boolean
  ): Seq[(Dependency, Either[VariantPublication, Publication], Artifact)] =
    for {
      dep <- {
        // need to keep reconciled versions to later on use projectCache0 when listing artifacts
        if (classpathOrder) orderedDependencies(keepReconciledVersions = true)
        else
          minimizedDependencies(withRetainedVersions = false, withReconciledVersions = true).toSeq
      }
      (source, proj) <- projectCache0
        .get(dep.moduleVersionConstraint)
        .toSeq

      classifiers0 =
        if (dep.attributes.classifier.isEmpty)
          classifiers
        else
          Some(classifiers.getOrElse(Nil) ++ Seq(dep.attributes.classifier))

      (pub, artifact) <- {
        val modArtifacts = source match {
          case modBased: ArtifactSource.ModuleBased =>
            modBased.moduleArtifacts(dep, proj).map {
              case (variantPub, art) =>
                (Left(variantPub), art)
            }
          case _ =>
            Nil
        }
        source.artifacts(dep, proj, classifiers0).map {
          case (pub, art) =>
            (Right(pub), art)
        } ++
          modArtifacts
      }
    } yield (dep, pub, artifact)

  @deprecated("Use the artifacts overload accepting types and classifiers instead", "1.1.0-M8")
  def classifiersArtifacts(classifiers: Seq[String]): Seq[Artifact] =
    artifacts(classifiers = Some(classifiers.map(Classifier(_))))

  @deprecated("Use artifacts overload accepting types and classifiers instead", "1.1.0-M8")
  def artifacts(withOptional: Boolean): Seq[Artifact] =
    artifacts()

  @deprecated("Use dependencyArtifacts overload accepting classifiers instead", "1.1.0-M8")
  def dependencyArtifacts(withOptional: Boolean): Seq[(Dependency, Artifact)] =
    dependencyArtifacts().map(t => (t._1, t._3))

  @deprecated("Use dependencyArtifacts overload accepting classifiers instead", "1.1.0-M8")
  def dependencyClassifiersArtifacts(classifiers: Seq[String]): Seq[(Dependency, Artifact)] =
    dependencyArtifacts(Some(classifiers.map(Classifier(_)))).map(t => (t._1, t._3))

  /** Returns errors on dependencies
    * @return
    *   errors
    */
  def errors0: Seq[(ModuleVersionConstraint, Seq[String])] = {
    val transitiveDepErrors = transitiveDependenciesAndErrors._1.map {
      case (dep, ex) =>
        (dep.moduleVersionConstraint, Seq(ex.message))
    }
    errorCache.toSeq ++ transitiveDepErrors
  }

  @deprecated("Use errors0 instead", "2.1.25")
  def errors: Seq[((Module, String), Seq[String])] =
    errors0.map {
      case ((mod, ver), value) =>
        ((mod, ver.asString), value)
    }

  @deprecated("Use errors0 instead", "1.1.0")
  def metadataErrors: Seq[((Module, String), Seq[String])] =
    errors0.map {
      case ((mod, v), messages) =>
        ((mod, v.asString), messages)
    }

  def dependenciesWithRetainedVersions: Set[Dependency] =
    dependencies.map { dep =>
      retainedVersions.get(dep.module).fold(dep) { v =>
        dep.withVersionConstraint(VersionConstraint0.fromVersion(v))
      }
    }

  /** Removes from this `Resolution` dependencies that are not in `dependencies` neither brought
    * transitively by them.
    *
    * This keeps the versions calculated by this `Resolution`. The common dependencies of different
    * subsets will thus be guaranteed to have the same versions.
    *
    * @param dependencies:
    *   the dependencies to keep from this `Resolution`
    */
  def subset0(dependencies: Seq[Dependency]): Either[DependencyError, Resolution] = {

    def updateVersion(dep: Dependency): Dependency =
      updated(
        dep,
        withRetainedVersions = false,
        withReconciledVersions = true,
        withFallbackConfig = false
      )

    @tailrec def helper(current: Set[Dependency]): Either[DependencyError, Set[Dependency]] = {
      val extraDepsOrErrors = current.toSeq.map(finalDependencies0)
      val errors = extraDepsOrErrors.collect {
        case Left(err) => err
      }

      errors match {
        case Seq() =>

          val newDeps   = current ++ extraDepsOrErrors.flatMap(_.toSeq).flatten.map(updateVersion)
          val anyNewDep = (newDeps -- current).nonEmpty

          if (anyNewDep)
            helper(newDeps)
          else
            Right(newDeps)
        case Seq(first, others @ _*) =>
          for (other <- others)
            first.addSuppressed(other)
          Left(first)
      }
    }

    val dependencies0 = dependencies
      .map(withDefaultConfig(_, defaultConfiguration))
      .map(updateVersion)

    val allDependenciesOrError = helper(dependencies0.toSet)
    allDependenciesOrError.map { allDependencies =>
      val subsetForceVersions = allDependencies.map(_.moduleVersionConstraint).toMap

      copyWithCache(
        rootDependencies = dependencies0,
        dependencySet = dependencySet.setValues(allDependencies)
        // don't know if something should be done about conflicts
      ).withForceVersions0(subsetForceVersions ++ forceVersions0)
    }
  }

  @deprecated("Use subset0 instead", "2.1.25")
  def subset(dependencies: Seq[Dependency]): Resolution =
    subset0(dependencies).toTry.get
}
