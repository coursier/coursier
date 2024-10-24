package coursier.core

import java.util.concurrent.ConcurrentHashMap

import coursier.util.Artifact

import scala.annotation.tailrec
import scala.collection.compat._
import scala.collection.compat.immutable.LazyList
import scala.collection.mutable
import scala.jdk.CollectionConverters._
import dataclass.data
import MinimizedExclusions._

object Resolution {

  type ModuleVersion = (Module, String)

  def profileIsActive(
    profile: Profile,
    properties: Map[String, String],
    osInfo: Activation.Os,
    jdkVersion: Option[Version],
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

  /** Get the active profiles of `project`, using the current properties `properties`, and
    * `profileActivations` stating if a profile is active.
    */
  def profiles(
    project: Project,
    properties: Map[String, String],
    osInfo: Activation.Os,
    jdkVersion: Option[Version],
    userActivations: Option[Map[String, Boolean]]
  ): Seq[Profile] =
    project.profiles.filter { profile =>
      profileIsActive(
        profile,
        properties,
        osInfo,
        jdkVersion,
        userActivations
      )
    }

  private object DepMgmt {
    def key(dep: Dependency): DependencyManagement.Key =
      DependencyManagement.Key(
        dep.module.organization,
        dep.module.name,
        if (dep.attributes.`type`.isEmpty) Type.jar else dep.attributes.`type`,
        dep.attributes.classifier
      )

    def addSeq(
      dict: DependencyManagement.Map,
      deps: Seq[(Configuration, Dependency)]
    ): DependencyManagement.Map =
      add(
        dict,
        deps.map {
          case (config, dep) =>
            val key0 = key(dep)
            val values = DependencyManagement.Values(
              config,
              dep.version,
              dep.minimizedExclusions,
              dep.optional
            )
            (key0, values)
        }
      )

    def add(
      dict: DependencyManagement.Map,
      deps: Seq[(DependencyManagement.Key, DependencyManagement.Values)]
    ): DependencyManagement.Map =
      if (deps.isEmpty)
        dict
      else {
        val b = new mutable.HashMap[DependencyManagement.Key, DependencyManagement.Values]
        b.sizeHint(dict.size + deps.length)
        b ++= dict
        val it = deps.iterator
        while (it.hasNext) {
          val (key0, incomingValues) = it.next()
          val newValues = b.get(key0) match {
            case Some(previousValues) =>
              previousValues.orElse(incomingValues)
            case None =>
              incomingValues
          }
          b += ((key0, newValues))
        }
        b.result().toMap
      }
  }

  def addDependencies(
    deps: Seq[Seq[(Configuration, Dependency)]]
  ): Seq[(Configuration, Dependency)] = {
    val (_, res) =
      deps.foldRight(Set.empty[DependencyManagement.Key], Seq.empty[(Configuration, Dependency)]) {
        case (deps0, (set, acc)) =>
          val deps = deps0.filter {
            case (_, dep) =>
              !set(DepMgmt.key(dep))
          }

          (set ++ deps.map { case (_, dep) => DepMgmt.key(dep) }, acc ++ deps)
      }

    res
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

  def withProperties(
    dependencies: Seq[(Configuration, Dependency)],
    properties: Map[String, String]
  ): Seq[(Configuration, Dependency)] =
    dependencies.map(withProperties(_, properties))

  /** Substitutes `properties` in `dependencies`.
    */
  private def withProperties(
    configDep: (Configuration, Dependency),
    properties: Map[String, String]
  ): (Configuration, Dependency) = {

    val (config, dep) = configDep

    def substituteTrimmedProps(s: String) =
      substituteProps(s, properties, trim = true)
    def substituteProps0(s: String) =
      substituteProps(s, properties, trim = false)

    val dep0 = dep.copy(
      module = dep.module.copy(
        organization = dep.module.organization.map(substituteProps0),
        name = dep.module.name.map(substituteProps0)
      ),
      version = substituteTrimmedProps(dep.version),
      attributes = dep.attributes
        .withType(dep.attributes.`type`.map(substituteProps0))
        .withClassifier(dep.attributes.classifier.map(substituteProps0)),
      configuration = dep.configuration.map(substituteProps0),
      minimizedExclusions = dep.minimizedExclusions.map(substituteProps0)
    )

    // FIXME The content of the optional tag may also be a property in
    // the original POM. Maybe not parse it that earlier?
    config.map(substituteProps0) -> dep0
  }

  /** Merge several dependencies, solving version constraints of duplicated modules.
    *
    * Returns the conflicted dependencies, and the merged others.
    */
  def merge(
    dependencies: Seq[Dependency],
    forceVersions: Map[Module, String],
    reconciliation: Option[Module => Reconciliation],
    preserveOrder: Boolean = false
  ): (Seq[Dependency], Seq[Dependency], Map[Module, String]) = {
    def reconcilerByMod(mod: Module): Reconciliation =
      reconciliation match {
        case Some(f) => f(mod)
        case _       => Reconciliation.Default
      }
    val dependencies0 = dependencies.toVector
    val mergedByModVer = dependencies0
      .groupBy(dep => dep.module)
      .map { case (module, deps) =>
        val anyOrgModule = module.withOrganization(Organization("*"))
        val forcedVersionOpt = forceVersions.get(module)
          .orElse(forceVersions.get(anyOrgModule))

        module -> {
          val (versionOpt, updatedDeps) = forcedVersionOpt match {
            case None =>
              if (deps.lengthCompare(1) == 0) (Some(deps.head.version), Right(deps))
              else {
                val versions   = deps.map(_.version)
                val reconciler = reconcilerByMod(module)
                val versionOpt = reconciler(versions)

                (
                  versionOpt,
                  versionOpt match {
                    case Some(version) =>
                      Right(deps.map(dep => dep.withVersion(version)))
                    case None =>
                      Left(deps)
                  }
                )
              }

            case Some(forcedVersion) =>
              (Some(forcedVersion), Right(deps.map(dep => dep.withVersion(forcedVersion))))
          }

          (updatedDeps, versionOpt)
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

  /** Applies `dependencyManagement` to `dependencies`.
    *
    * Fill empty version / scope / exclusions, for dependencies found in `dependencyManagement`.
    */
  def depsWithDependencyManagement(
    dependencies: Seq[(Configuration, Dependency)],
    overrides: DependencyManagement.Map,
    dependencyManagement: Seq[(Configuration, Dependency)],
    forceDepMgmtVersions: Boolean
  ): Seq[(Configuration, Dependency)] = {

    // See http://maven.apache.org/guides/introduction/introduction-to-dependency-mechanism.html#Dependency_Management

    lazy val dict = DepMgmt.addSeq(overrides, dependencyManagement)

    lazy val dict0 =
      if (forceDepMgmtVersions) dict
      else {
        val clearDepMgmtVersion = dependencies
          .map(_._2)
          .groupBy(DepMgmt.key)
          .collect {
            case (k, l) if !overrides.contains(k) && l.forall(_.version.nonEmpty) =>
              k
          }
          .toSet
        dict
          .map {
            case (k, v) =>
              k -> {
                if (clearDepMgmtVersion(k)) v.withVersion("")
                else v
              }
          }
          .filter(!_._2.isEmpty)
      }
    dependencies.map {
      case (config0, dep0) =>
        var config = config0
        var dep    = dep0

        val key = DepMgmt.key(dep0)
        for (mgmtValues <- dict.get(key)) {

          val useManagedVersion =
            mgmtValues.version.nonEmpty &&
            (forceDepMgmtVersions || dep.version.isEmpty || overrides.contains(key))
          if (useManagedVersion)
            dep = dep.withVersion(mgmtValues.version)

          if (config.isEmpty)
            config = mgmtValues.config

          // FIXME The version and scope/config from dependency management, if any, are substituted
          // no matter what. The same is not done for the exclusions and optionality, for a lack of
          // way of distinguishing empty exclusions from no exclusion section and optional set to
          // false from no optional section in the dependency management for now.

          if (dep.minimizedExclusions.isEmpty)
            dep = dep.withMinimizedExclusions(mgmtValues.minimizedExclusions)

          if (mgmtValues.optional)
            dep = dep.withOptional(mgmtValues.optional)
        }

        if (dict.nonEmpty)
          dep = dep.withOverrides {
            if (dep.overrides.isEmpty)
              dict0
            else if (dict0.isEmpty)
              dep.overrides
            else
              DepMgmt.add(dict0, dep.overrides.toSeq)
          }

        (config, dep)
    }
  }

  @deprecated(
    "Use the override accepting an override map and forceDepMgmtVersions instead",
    "2.1.15"
  )
  def depsWithDependencyManagement(
    dependencies: Seq[(Configuration, Dependency)],
    dependencyManagement: Seq[(Configuration, Dependency)]
  ): Seq[(Configuration, Dependency)] =
    depsWithDependencyManagement(
      dependencies,
      Map.empty,
      dependencyManagement,
      forceDepMgmtVersions = false
    )

  private def withDefaultConfig(dep: Dependency, defaultConfiguration: Configuration): Dependency =
    if (dep.configuration.isEmpty)
      dep.withConfiguration(defaultConfiguration)
    else
      dep

  /** Filters `dependencies` with `exclusions`.
    */
  def withExclusions(
    dependencies: Seq[(Configuration, Dependency)],
    exclusions: Set[(Organization, ModuleName)]
  ): Seq[(Configuration, Dependency)] = {
    val minimizedExclusions = MinimizedExclusions(exclusions)

    dependencies
      .filter {
        case (_, dep) =>
          minimizedExclusions(dep.module.organization, dep.module.name)
      }
      .map {
        case (config, dep) =>
          config -> dep.withMinimizedExclusions(dep.minimizedExclusions.join(minimizedExclusions))
      }
  }

  def withParentConfigurations(
    config: Configuration,
    configurations: Map[Configuration, Seq[Configuration]]
  ): (Configuration, Set[Configuration]) = {
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

    val config0 = Parse.withFallbackConfig(config) match {
      case Some((main, fallback)) =>
        if (configurations.contains(main))
          main
        else if (configurations.contains(fallback))
          fallback
        else
          main
      case None => config
    }

    (config0, helper(Set(config0), Set.empty))
  }

  private val mavenScopes = {

    val base = Map[Configuration, Set[Configuration]](
      Configuration.compile -> Set(Configuration.compile),
      Configuration.optional -> Set(
        Configuration.compile,
        Configuration.optional,
        Configuration.runtime
      ),
      Configuration.provided -> Set(),
      Configuration.runtime  -> Set(Configuration.compile, Configuration.runtime),
      Configuration.test -> Set(Configuration.compile, Configuration.runtime, Configuration.test)
    )

    base ++ Seq(
      Configuration.default -> base(Configuration.runtime)
    )
  }

  def projectProperties(project: Project): Seq[(String, String)] = {

    val packaging = project.packagingOpt.getOrElse(Type.jar)

    // FIXME The extra properties should only be added for Maven projects, not Ivy ones
    val properties0 = project.properties ++ Seq(
      // some artifacts seem to require these (e.g. org.jmock:jmock-legacy:2.5.1)
      // although I can find no mention of them in any manual / spec
      "pom.groupId"    -> project.module.organization.value,
      "pom.artifactId" -> project.module.name.value,
      "pom.version"    -> project.actualVersion,
      // Required by some dependencies too (org.apache.directory.shared:shared-ldap:0.9.19 in particular)
      "groupId"            -> project.module.organization.value,
      "artifactId"         -> project.module.name.value,
      "version"            -> project.actualVersion,
      "project.groupId"    -> project.module.organization.value,
      "project.artifactId" -> project.module.name.value,
      "project.version"    -> project.actualVersion,
      "project.packaging"  -> packaging.value
    ) ++ project.parent.toSeq.flatMap {
      case (parModule, parVersion) =>
        Seq(
          "project.parent.groupId"    -> parModule.organization.value,
          "project.parent.artifactId" -> parModule.name.value,
          "project.parent.version"    -> parVersion,
          "parent.groupId"            -> parModule.organization.value,
          "parent.artifactId"         -> parModule.name.value,
          "parent.version"            -> parVersion
        )
    }

    // loose attempt at substituting properties in each others in properties0
    // doesn't try to go recursive for now, but that could be made so if necessary

    substitute(properties0)
  }

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
    projectCache: ((Module, String)) => Option[Project]
  ): LazyList[Project] =
    project.parent.flatMap(projectCache) match {
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
    projectCache: ((Module, String)) => Option[Project],
    keepProvidedDependencies: Boolean,
    forceDepMgmtVersions: Boolean
  ): Seq[Dependency] = {

    // section numbers in the comments refer to withDependencyManagement

    val parentProperties0 = parents(project, projectCache)
      .toVector
      .flatMap(_.properties)

    val project0 = withFinalProperties(
      project.withProperties(parentProperties0 ++ project.properties)
    )
    val properties = project0.properties.toMap

    val (actualConfig, configurations) = withParentConfigurations(
      if (from.configuration.isEmpty) defaultConfiguration else from.configuration,
      project0.configurations
    )

    // Vague attempt at making the Maven scope model fit into the Ivy configuration one

    val withProvidedOpt =
      if (keepProvidedDependencies) Some(Configuration.provided)
      else None
    val keepOpt = mavenScopes.get(actualConfig).map(_ ++ withProvidedOpt)

    withExclusions(
      // 2.1 & 2.2
      depsWithDependencyManagement(
        // 1.7
        withProperties(project0.dependencies, properties),
        from.overrides,
        withProperties(project0.dependencyManagement, properties),
        forceDepMgmtVersions = forceDepMgmtVersions
      ),
      from.minimizedExclusions.toSet()
    )
      .flatMap {
        case (config0, dep0) =>
          // Dependencies from Maven verify
          //   dep.configuration.isEmpty
          // and expect dep.configuration to be filled here

          val dep =
            if (from.optional)
              dep0.withOptional(true)
            else
              dep0

          val config = if (config0.isEmpty) Configuration.compile else config0

          def default =
            if (configurations(config))
              Seq(dep)
            else
              Nil

          if (dep.configuration.nonEmpty)
            default
          else
            keepOpt.fold(default) { keep =>
              if (keep(config)) {
                val depConfig =
                  if (actualConfig == Configuration.test || actualConfig == Configuration.runtime)
                    Configuration.runtime
                  else
                    defaultConfiguration

                Seq(dep.withConfiguration(depConfig))
              }
              else
                Nil
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

  def overrideScalaModule(sv: String): Dependency => Dependency =
    overrideScalaModule(sv, Organization("org.scala-lang"))

  def overrideScalaModule(sv: String, scalaOrg: Organization): Dependency => Dependency = {
    val sbv = sv.split('.').take(2).mkString(".")
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
        dep.withVersion(sv)
      else
        dep
  }

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
    overrideScalaModule(sv) andThen overrideFullSuffix(sv)

  private def fallbackConfigIfNecessary(dep: Dependency, configs: Set[Configuration]): Dependency =
    Parse.withFallbackConfig(dep.configuration) match {
      case Some((main, fallback)) =>
        val config0 =
          if (configs(main))
            main
          else if (configs(fallback))
            fallback
          else
            dep.configuration

        dep.withConfiguration(config0)
      case _ =>
        dep
    }

  private def withFinalProperties(project: Project): Project =
    project.withProperties(projectProperties(project))

}

/** State of a dependency resolution.
  *
  * Done if method `isDone` returns `true`.
  *
  * @param conflicts:
  *   conflicting dependencies
  * @param projectCache:
  *   cache of known projects
  * @param errorCache:
  *   keeps track of the modules whose project definition could not be found
  */
@data class Resolution(
  rootDependencies: Seq[Dependency] = Nil,
  dependencySet: DependencySet = DependencySet.empty,
  forceVersions: Map[Module, String] = Map.empty,
  conflicts: Set[Dependency] = Set.empty,
  projectCache: Map[Resolution.ModuleVersion, (ArtifactSource, Project)] = Map.empty,
  errorCache: Map[Resolution.ModuleVersion, Seq[String]] = Map.empty,
  finalDependenciesCache: Map[Dependency, Seq[Dependency]] = Map.empty,
  filter: Option[Dependency => Boolean] = None,
  reconciliation: Option[Module => Reconciliation] = None,
  osInfo: Activation.Os = Activation.Os.empty,
  jdkVersion: Option[Version] = None,
  userActivations: Option[Map[String, Boolean]] = None,
  mapDependencies: Option[Dependency => Dependency] = None,
  extraProperties: Seq[(String, String)] = Nil,
  forceProperties: Map[String, String] = Map.empty, // FIXME Make that a seq too?
  defaultConfiguration: Configuration = Configuration.defaultCompile,
  @since("2.1.9")
  keepProvidedDependencies: Boolean = false,
  @since("2.1.15")
  forceDepMgmtVersions: Boolean = false
) {

  lazy val dependencies: Set[Dependency] =
    dependencySet.set

  override lazy val hashCode: Int = {
    var code = 17 + "coursier.core.Resolution".##
    code = 37 * code + tuple.##
    37 * code
  }

  def withDependencies(dependencies: Set[Dependency]): Resolution =
    withDependencySet(dependencySet.setValues(dependencies))

  def addToErrorCache(entries: Iterable[(Resolution.ModuleVersion, Seq[String])]): Resolution =
    copyWithCache(
      errorCache = errorCache ++ entries
    )

  private def copyWithCache(
    rootDependencies: Seq[Dependency] = rootDependencies,
    dependencySet: DependencySet = dependencySet,
    conflicts: Set[Dependency] = conflicts,
    errorCache: Map[Resolution.ModuleVersion, Seq[String]] = errorCache
    // don't allow changing mapDependencies here - that would invalidate finalDependenciesCache
    // don't allow changing projectCache here - use addToProjectCache that takes forceProperties into account
  ): Resolution =
    withRootDependencies(rootDependencies)
      .withDependencySet(dependencySet)
      .withConflicts(conflicts)
      .withErrorCache(errorCache)
      .withFinalDependenciesCache(finalDependenciesCache ++ finalDependenciesCache0.asScala)

  def addToProjectCache(
    projects: (Resolution.ModuleVersion, (ArtifactSource, Project))*
  ): Resolution = {

    val duplicates = projects
      .collect {
        case (modVer, _) if projectCache.contains(modVer) =>
          modVer
      }

    assert(
      duplicates.isEmpty,
      s"Projects already added in resolution: ${duplicates.mkString(", ")}"
    )

    withFinalDependenciesCache(finalDependenciesCache ++ finalDependenciesCache0.asScala)
      .withProjectCache {
        projectCache ++ projects.map {
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

  import Resolution._

  private[core] val finalDependenciesCache0 = new ConcurrentHashMap[Dependency, Seq[Dependency]]

  private def finalDependencies0(dep: Dependency): Seq[Dependency] =
    if (dep.transitive) {
      val deps = finalDependenciesCache.getOrElse(dep, finalDependenciesCache0.get(dep))

      if (deps == null)
        projectCache.get(dep.moduleVersion) match {
          case Some((_, proj)) =>
            val res0 = finalDependencies(
              dep,
              proj,
              defaultConfiguration,
              k => projectCache.get(k).map(_._2),
              keepProvidedDependencies,
              forceDepMgmtVersions
            ).filter(filter getOrElse defaultFilter)
            val res = mapDependencies.fold(res0)(res0.map(_))
            finalDependenciesCache0.put(dep, res)
            res
          case None => Nil
        }
      else
        deps
    }
    else
      Nil

  def dependenciesOf(dep: Dependency): Seq[Dependency] =
    dependenciesOf(dep, withRetainedVersions = false)

  def dependenciesOf(dep: Dependency, withRetainedVersions: Boolean): Seq[Dependency] =
    dependenciesOf(dep, withRetainedVersions = withRetainedVersions, withFallbackConfig = false)

  private def configsOf(dep: Dependency): Set[Configuration] =
    projectCache
      .get(dep.moduleVersion)
      .map(_._2.configurations.keySet)
      .getOrElse(Set.empty)

  private def updated(
    dep: Dependency,
    withRetainedVersions: Boolean,
    withFallbackConfig: Boolean
  ): Dependency = {
    val dep0 =
      if (withRetainedVersions)
        dep.withVersion(retainedVersions.getOrElse(dep.module, dep.version))
      else
        dep
    if (withFallbackConfig)
      Resolution.fallbackConfigIfNecessary(dep0, configsOf(dep0))
    else
      dep0
  }

  def dependenciesOf(
    dep: Dependency,
    withRetainedVersions: Boolean,
    withFallbackConfig: Boolean
  ): Seq[Dependency] = {
    val deps = finalDependencies0(dep)
    if (withRetainedVersions || withFallbackConfig)
      deps.map(updated(_, withRetainedVersions, withFallbackConfig))
    else
      deps
  }

  /** Transitive dependencies of the current dependencies, according to what there currently is in
    * cache.
    *
    * No attempt is made to solve version conflicts here.
    */
  lazy val transitiveDependencies: Seq[Dependency] =
    (dependencySet.minimizedSet -- conflicts)
      .toVector
      .flatMap(finalDependencies0)

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
  lazy val nextDependenciesAndConflicts: (Seq[Dependency], Seq[Dependency], Map[Module, String]) =
    // TODO Provide the modules whose version was forced by dependency overrides too
    merge(
      rootDependencies.map(withDefaultConfig(_, defaultConfiguration)) ++ transitiveDependencies,
      forceVersions,
      reconciliation
    )

  private def updatedRootDependencies =
    merge(
      rootDependencies.map(withDefaultConfig(_, defaultConfiguration)),
      forceVersions,
      reconciliation,
      preserveOrder = true
    )._2

  lazy val reconciledVersions: Map[Module, String] =
    nextDependenciesAndConflicts._3.map {
      case k @ (m, v) =>
        m -> projectCache.get(k).fold(v)(_._2.version)
    }

  def retainedVersions: Map[Module, String] =
    nextDependenciesAndConflicts._3

  /** The modules we miss some info about.
    */
  lazy val missingFromCache: Set[ModuleVersion] = {
    val modules = dependencies
      .map(_.moduleVersion)
    val nextModules = nextDependenciesAndConflicts._2
      .map(_.moduleVersion)

    (modules ++ nextModules)
      .filterNot(mod => projectCache.contains(mod) || errorCache.contains(mod))
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

  private def eraseVersion(dep: Dependency) =
    dep.withVersion("")

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
        trDep <- finalDependencies0(dep)
      } yield eraseVersion(trDep) -> eraseVersion(dep)

    val knownDeps = (updatedDeps ++ updatedConflicts)
      .map(eraseVersion)
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
    val rootDependencies0 = rootDependencies
      .map(withDefaultConfig(_, defaultConfiguration))
      .map(eraseVersion)
      .toSet

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
      .filter(dep => remainingDependencies0(eraseVersion(dep)))
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
  def dependencyManagementRequirements(
    project: Project
  ): Set[ModuleVersion] = {

    val needsParent =
      project.parent.exists { par =>
        val parentFound = projectCache.contains(par) || errorCache.contains(par)
        !parentFound
      }

    if (needsParent)
      project.parent.toSet
    else {

      val parentProperties0 = parents(project, k => projectCache.get(k).map(_._2))
        .toVector
        .flatMap(_.properties)

      // 1.1 (see above)
      val approxProperties = parentProperties0.toMap ++ projectProperties(project)

      val profiles0 = profiles(
        project,
        approxProperties,
        osInfo,
        jdkVersion,
        userActivations
      )

      val profileDependencies = profiles0.flatMap(p => p.dependencies ++ p.dependencyManagement)

      val project0 =
        project.withProperties(
          project.properties ++ profiles0.flatMap(_.properties)
        ) // belongs to 1.5 & 1.6

      val propertiesMap0 = withFinalProperties(
        project0.withProperties(parentProperties0 ++ project0.properties)
      ).properties.toMap

      val modules = withProperties(
        project0.dependencies ++ project0.dependencyManagement ++ profileDependencies,
        propertiesMap0
      ).collect {
        case (Configuration.`import`, dep) => dep.moduleVersion
      }

      modules.toSet
    }
  }

  /** Missing modules in cache, to get the full list of dependencies of `project`, taking dependency
    * management / inheritance into account.
    *
    * Note that adding the missing modules to the cache may unveil other missing modules, so these
    * modules should be added to the cache, and `dependencyManagementMissing` checked again for new
    * missing modules.
    */
  def dependencyManagementMissing(project: Project): Set[ModuleVersion] = {

    @tailrec
    def helper(
      toCheck: Set[ModuleVersion],
      done: Set[ModuleVersion],
      missing: Set[ModuleVersion]
    ): Set[ModuleVersion] =
      if (toCheck.isEmpty)
        missing
      else if (toCheck.exists(done))
        helper(toCheck -- done, done, missing)
      else if (toCheck.exists(missing))
        helper(toCheck -- missing, done, missing)
      else if (toCheck.exists(projectCache.contains)) {
        val (checking, remaining) = toCheck.partition(projectCache.contains)
        val directRequirements = checking
          .flatMap(mod => dependencyManagementRequirements(projectCache(mod)._2))

        helper(remaining ++ directRequirements, done ++ checking, missing)
      }
      else if (toCheck.exists(errorCache.contains)) {
        val (errored, remaining) = toCheck.partition(errorCache.contains)
        helper(remaining, done ++ errored, missing)
      }
      else
        helper(Set.empty, done, missing ++ toCheck)

    helper(
      dependencyManagementRequirements(project),
      Set(project.moduleVersion),
      Set.empty
    )
  }

  /** Add dependency management / inheritance related items to `project`, from what's available in
    * cache.
    *
    * It is recommended to have fetched what `dependencyManagementMissing` returned prior to calling
    * this.
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

    val parentProperties0 = parents(project, k => projectCache.get(k).map(_._2))
      .toVector
      .flatMap(_.properties)

    // 1.1 (see above)
    val approxProperties = parentProperties0.toMap ++ projectProperties(project)

    val profiles0 = profiles(
      project,
      approxProperties,
      osInfo,
      jdkVersion,
      userActivations
    )

    // 1.2 made from Pom.scala (TODO look at the very details?)

    // 1.3 & 1.4 (if only vaguely so)
    val project0 =
      project.withProperties(
        project.properties ++ profiles0.flatMap(_.properties)
      ) // belongs to 1.5 & 1.6

    val propertiesMap0 = withFinalProperties(
      project0.withProperties(parentProperties0 ++ project0.properties)
    ).properties.toMap

    val (importDeps, standardDeps) = {

      val dependencies0 = addDependencies(project0.dependencies +: profiles0.map(_.dependencies))

      val (importDeps0, standardDeps0) = dependencies0
        .map { dep =>
          val dep0 = withProperties(dep, propertiesMap0)
          if (dep0._1 == Configuration.`import`)
            (dep0._2 :: Nil, Nil)
          else
            (Nil, dep :: Nil) // not dep0 (properties with be substituted later)
        }
        .unzip

      (importDeps0.flatten, standardDeps0.flatten)
    }

    val importDepsMgmt = {

      val dependenciesMgmt0 =
        addDependencies(project0.dependencyManagement +: profiles0.map(_.dependencyManagement))

      dependenciesMgmt0.flatMap { dep =>
        val (conf0, dep0) = withProperties(dep, propertiesMap0)
        if (conf0 == Configuration.`import`)
          dep0 :: Nil
        else
          Nil
      }
    }

    val parentDeps =
      importDeps.map(_.moduleVersion) ++
        importDepsMgmt.map(_.moduleVersion) ++
        project0.parent // belongs to 1.5 & 1.6

    val retainedParentDeps = parentDeps.filter(projectCache.contains)

    val retainedParentProjects = retainedParentDeps.map(projectCache(_)._2)

    val depMgmt = (
      project0.dependencyManagement +: (
        profiles0.map(_.dependencyManagement) ++
          retainedParentProjects.map { p =>
            val parentProperties0 = parents(p, k => projectCache.get(k).map(_._2))
              .toVector
              .flatMap(_.properties)
            val props = withFinalProperties(
              p.withProperties(parentProperties0 ++ p.properties)
            ).properties.toMap
            withProperties(p.dependencyManagement, props)
          }
      )
    )
      .foldLeft(Map.empty[DependencyManagement.Key, DependencyManagement.Values])(DepMgmt.addSeq)

    val retainedParentDepsSet = retainedParentDeps.toSet

    project0
      .withPackagingOpt(project0.packagingOpt.map(_.map(substituteProps(_, propertiesMap0))))
      .withVersion(substituteProps(project0.version, propertiesMap0))
      .withDependencies(
        standardDeps ++
          project0.parent // belongs to 1.5 & 1.6
            .filter(projectCache.contains)
            .toSeq
            .flatMap(projectCache(_)._2.dependencies)
      )
      .withDependencyManagement(
        depMgmt.toList.collect {
          case (key, values) if values.config != Configuration.`import` =>
            (values.config, values.fakeDependency(key))
        }
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
    dependencySet.minimizedSet.map { dep =>
      Resolution.fallbackConfigIfNecessary(dep, configsOf(dep))
    }

  private def orderedDependencies0(keepOverrides: Boolean): Seq[Dependency] = {

    def helper(deps: List[Dependency], done: DependencySet): LazyList[Dependency] =
      deps match {
        case Nil => LazyList.empty
        case h :: t =>
          val h0 = if (keepOverrides) h else h.clearOverrides
          if (done.covers(h0))
            helper(t, done)
          else {
            lazy val done0 = done.add(h0)
            val todo = dependenciesOf(h, withRetainedVersions = true, withFallbackConfig = true)
              // filtering with done0 rather than done for some cycles (dependencies having themselves as dependency)
              .filter(!done0.covers(_))
            val t0 =
              if (todo.isEmpty) t
              else t ::: todo.toList
            h0 #:: helper(t0, done0)
          }
      }

    val rootDeps = updatedRootDependencies
      .map(withDefaultConfig(_, defaultConfiguration))
      .map(dep => updated(dep, withRetainedVersions = true, withFallbackConfig = true))
      .toList

    helper(rootDeps, DependencySet.empty).toVector
  }

  def orderedDependencies: Seq[Dependency] =
    orderedDependencies0(keepOverrides = false)
  def orderedDependenciesWithOverrides: Seq[Dependency] =
    orderedDependencies0(keepOverrides = true)

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
    dependencyArtifacts(classifiers)
      .collect {
        case (_, pub, artifact) if types(pub.`type`) =>
          artifact
      }
      .distinct

  def dependencyArtifacts(): Seq[(Dependency, Publication, Artifact)] =
    dependencyArtifacts(None)

  def dependencyArtifacts(
    classifiers: Option[Seq[Classifier]]
  ): Seq[(Dependency, Publication, Artifact)] =
    dependencyArtifacts(classifiers, classpathOrder = true)

  def dependencyArtifacts(
    classifiers: Option[Seq[Classifier]],
    classpathOrder: Boolean
  ): Seq[(Dependency, Publication, Artifact)] =
    for {
      dep <- if (classpathOrder) orderedDependencies else minDependencies.toSeq
      (source, proj) <- projectCache
        .get(dep.moduleVersion)
        .toSeq

      classifiers0 =
        if (dep.attributes.classifier.isEmpty)
          classifiers
        else
          Some(classifiers.getOrElse(Nil) ++ Seq(dep.attributes.classifier))

      (pub, artifact) <- source.artifacts(dep, proj, classifiers0)
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
  def errors: Seq[(ModuleVersion, Seq[String])] = errorCache.toSeq

  @deprecated("Use errors instead", "1.1.0")
  def metadataErrors: Seq[(ModuleVersion, Seq[String])] = errors

  def dependenciesWithRetainedVersions: Set[Dependency] =
    dependencies.map { dep =>
      retainedVersions.get(dep.module).fold(dep) { v =>
        dep.withVersion(v)
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
  def subset(dependencies: Seq[Dependency]): Resolution = {

    def updateVersion(dep: Dependency): Dependency =
      dep.withVersion(retainedVersions.getOrElse(dep.module, dep.version))

    @tailrec def helper(current: Set[Dependency]): Set[Dependency] = {
      val newDeps = current ++ current
        .flatMap(finalDependencies0)
        .map(updateVersion)

      val anyNewDep = (newDeps -- current).nonEmpty

      if (anyNewDep)
        helper(newDeps)
      else
        newDeps
    }

    val dependencies0 = dependencies
      .map(withDefaultConfig(_, defaultConfiguration))
      .map(dep => updated(dep, withRetainedVersions = true, withFallbackConfig = true))

    val allDependencies     = helper(dependencies0.toSet)
    val subsetForceVersions = allDependencies.map(_.moduleVersion).toMap

    copyWithCache(
      rootDependencies = dependencies0,
      dependencySet = dependencySet.setValues(allDependencies)
      // don't know if something should be done about conflicts
    ).withForceVersions(subsetForceVersions ++ forceVersions)
  }
}
