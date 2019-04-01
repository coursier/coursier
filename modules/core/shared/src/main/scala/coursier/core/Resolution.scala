package coursier.core

import java.util.concurrent.ConcurrentHashMap
import java.util.regex.Pattern.quote

import scala.annotation.tailrec
import scala.collection.JavaConverters._

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
        if (profile.activeByDefault.toSeq.contains(true))
          Some(true)
        else
          None
    }

    def fromActivation = profile.activation.isActive(properties, osInfo, jdkVersion)

    fromUserOrDefault.getOrElse(fromActivation)
  }

  /**
   * Get the active profiles of `project`, using the current properties `properties`,
   * and `profileActivations` stating if a profile is active.
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

  object DepMgmt {
    type Key = (Organization, ModuleName, Type)

    def key(dep: Dependency): Key =
      (dep.module.organization, dep.module.name, if (dep.attributes.`type`.isEmpty) Type.jar else dep.attributes.`type`)

    def add(
      dict: Map[Key, (Configuration, Dependency)],
      item: (Configuration, Dependency)
    ): Map[Key, (Configuration, Dependency)] = {

      val key0 = key(item._2)

      if (dict.contains(key0))
        dict
      else
        dict + (key0 -> item)
    }

    def addSeq(
      dict: Map[Key, (Configuration, Dependency)],
      deps: Seq[(Configuration, Dependency)]
    ): Map[Key, (Configuration, Dependency)] =
      (dict /: deps)(add)
  }

  def addDependencies(deps: Seq[Seq[(Configuration, Dependency)]]): Seq[(Configuration, Dependency)] = {
    val res =
      (deps :\ (Set.empty[DepMgmt.Key], Seq.empty[(Configuration, Dependency)])) {
        case (deps0, (set, acc)) =>
          val deps = deps0
            .filter{case (_, dep) => !set(DepMgmt.key(dep))}

          (set ++ deps.map{case (_, dep) => DepMgmt.key(dep)}, acc ++ deps)
      }

    res._2
  }

  def hasProps(s: String): Boolean = {

    var ok = false
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

  def substituteProps(s: String, properties: Map[String, String]): String = {

    // this method is called _very_ often, hence the micro-optimization

    var b: java.lang.StringBuilder = null
    var idx = 0

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
      } else {
        idx = idx + 2 + name.length + 1 // == endIdx + 1
        properties.get(name) match {
          case None =>
            b.append(s, dolIdx, idx)
          case Some(v) =>
            b.append(v)
        }
      }
    }

    if (b == null)
      s
    else
      b.toString
  }

  /**
   * Substitutes `properties` in `dependencies`.
   */
  def withProperties(
    dependencies: Seq[(Configuration, Dependency)],
    properties: Map[String, String]
  ): Seq[(Configuration, Dependency)] = {

    def substituteProps0(s: String) =
      substituteProps(s, properties)

    dependencies.map {
      case (config, dep) =>
        config.map(substituteProps0) -> dep.copy(
          module = dep.module.copy(
            organization = dep.module.organization.map(substituteProps0),
            name = dep.module.name.map(substituteProps0)
          ),
          version = substituteProps0(dep.version),
          attributes = dep.attributes.copy(
            `type` = dep.attributes.`type`.map(substituteProps0),
            classifier = dep.attributes.classifier.map(substituteProps0)
          ),
          configuration = dep.configuration.map(substituteProps0),
          exclusions = dep.exclusions.map {
            case (org, name) =>
              (org.map(substituteProps0), name.map(substituteProps0))
          }
          // FIXME The content of the optional tag may also be a property in
          // the original POM. Maybe not parse it that earlier?
        )
    }
  }

  /**
   * Merge several version constraints together.
   *
   * Returns `None` in case of conflict.
   */
  def mergeVersions(versions: Seq[String]): Option[String] = {

    val parsedConstraints = versions.map(Parse.versionConstraint)

    VersionConstraint
      .merge(parsedConstraints: _*)
      .flatMap(_.repr)
  }

  /**
   * Merge several dependencies, solving version constraints of duplicated
   * modules.
   *
   * Returns the conflicted dependencies, and the merged others.
   */
  def merge(
    dependencies: TraversableOnce[Dependency],
    forceVersions: Map[Module, String]
  ): (Seq[Dependency], Seq[Dependency], Map[Module, String]) = {

    val mergedByModVer = dependencies
      .toVector
      .groupBy(dep => dep.module)
      .map { case (module, deps) =>
        val anyOrgModule = module.copy(organization = Organization("*"))
        val forcedVersionOpt = forceVersions.get(module)
          .orElse(forceVersions.get(anyOrgModule))

        module -> {
          val (versionOpt, updatedDeps) = forcedVersionOpt match {
            case None =>
              if (deps.lengthCompare(1) == 0) (Some(deps.head.version), Right(deps))
              else {
                val versions = deps
                  .map(_.version)
                  .distinct
                val versionOpt = mergeVersions(versions)

                (versionOpt, versionOpt match {
                  case Some(version) =>
                    Right(deps.map(dep => dep.copy(version = version)))
                  case None =>
                    Left(deps)
                })
              }

            case Some(forcedVersion) =>
              (Some(forcedVersion), Right(deps.map(dep => dep.copy(version = forcedVersion))))
          }

          (updatedDeps, versionOpt)
        }
      }

    val merged = mergedByModVer
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

  /**
   * Applies `dependencyManagement` to `dependencies`.
   *
   * Fill empty version / scope / exclusions, for dependencies found in
   * `dependencyManagement`.
   */
  def depsWithDependencyManagement(
    dependencies: Seq[(Configuration, Dependency)],
    dependencyManagement: Seq[(Configuration, Dependency)]
  ): Seq[(Configuration, Dependency)] = {

    // See http://maven.apache.org/guides/introduction/introduction-to-dependency-mechanism.html#Dependency_Management

    lazy val dict = DepMgmt.addSeq(Map.empty, dependencyManagement)

    dependencies.map {
      case (config0, dep0) =>
        var config = config0
        var dep = dep0

        for ((mgmtConfig, mgmtDep) <- dict.get(DepMgmt.key(dep0))) {

          if (mgmtDep.version.nonEmpty)
            dep = dep.copy(version = mgmtDep.version)

          if (config.isEmpty)
            config = mgmtConfig

          // FIXME The version and scope/config from dependency management, if any, are substituted
          // no matter what. The same is not done for the exclusions and optionality, for a lack of
          // way of distinguishing empty exclusions from no exclusion section and optional set to
          // false from no optional section in the dependency management for now.

          if (dep.exclusions.isEmpty)
            dep = dep.copy(exclusions = mgmtDep.exclusions)

          if (mgmtDep.optional)
            dep = dep.copy(optional = mgmtDep.optional)
        }

        (config, dep)
    }
  }


  val defaultConfiguration = Configuration.compile

  def withDefaultConfig(dep: Dependency): Dependency =
    if (dep.configuration.isEmpty)
      dep.copy(configuration = defaultConfiguration)
    else
      dep

  /**
   * Filters `dependencies` with `exclusions`.
   */
  def withExclusions(
    dependencies: Seq[(Configuration, Dependency)],
    exclusions: Set[(Organization, ModuleName)]
  ): Seq[(Configuration, Dependency)] = {

    val filter = Exclusions(exclusions)

    dependencies
      .filter {
        case (_, dep) =>
          filter(dep.module.organization, dep.module.name)
      }
      .map {
        case (config, dep) =>
          config -> dep.copy(
            exclusions = Exclusions.minimize(dep.exclusions ++ exclusions)
          )
      }
  }

  def withParentConfigurations(config: Configuration, configurations: Map[Configuration, Seq[Configuration]]): (Configuration, Set[Configuration]) = {
    @tailrec
    def helper(configs: Set[Configuration], acc: Set[Configuration]): Set[Configuration] =
      if (configs.isEmpty)
        acc
      else if (configs.exists(acc))
        helper(configs -- acc, acc)
      else if (configs.exists(!configurations.contains(_))) {
        val (remaining, notFound) = configs.partition(configurations.contains)
        helper(remaining, acc ++ notFound)
      } else {
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
      Configuration.optional -> Set(Configuration.compile, Configuration.optional, Configuration.runtime),
      Configuration.provided -> Set(),
      Configuration.runtime -> Set(Configuration.compile, Configuration.runtime),
      Configuration.test -> Set()
    )

    base ++ Seq(
      Configuration.default -> base(Configuration.runtime)
    )
  }

  def projectProperties(project: Project): Seq[(String, String)] = {

    // vague attempt at recovering the POM packaging tag
    val packagingOpt = project.publications.collectFirst {
      case (Configuration.compile, pub) =>
        pub.`type`
    }

    // FIXME The extra properties should only be added for Maven projects, not Ivy ones
    val properties0 = project.properties ++ Seq(
      // some artifacts seem to require these (e.g. org.jmock:jmock-legacy:2.5.1)
      // although I can find no mention of them in any manual / spec
      "pom.groupId"         -> project.module.organization.value,
      "pom.artifactId"      -> project.module.name.value,
      "pom.version"         -> project.actualVersion,
      // Required by some dependencies too (org.apache.directory.shared:shared-ldap:0.9.19 in particular)
      "groupId"             -> project.module.organization.value,
      "artifactId"          -> project.module.name.value,
      "version"             -> project.actualVersion,
      "project.groupId"     -> project.module.organization.value,
      "project.artifactId"  -> project.module.name.value,
      "project.version"     -> project.actualVersion
    ) ++ packagingOpt.toSeq.map { packaging =>
      "project.packaging"   -> packaging.value
    } ++ project.parent.toSeq.flatMap {
      case (parModule, parVersion) =>
        Seq(
          "project.parent.groupId"     -> parModule.organization.value,
          "project.parent.artifactId"  -> parModule.name.value,
          "project.parent.version"     -> parVersion,
          "parent.groupId"     -> parModule.organization.value,
          "parent.artifactId"  -> parModule.name.value,
          "parent.version"     -> parVersion
        )
    }

    // loose attempt at substituting properties in each others in properties0
    // doesn't try to go recursive for now, but that could be made so if necessary

    substitute(properties0)
  }

  private def substitute(properties0: Seq[(String, String)]): Seq[(String, String)] = {

    val done = properties0
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

  /**
   * Get the dependencies of `project`, knowing that it came from dependency
   * `from` (that is, `from.module == project.module`).
   *
   * Substitute properties, update scopes, apply exclusions, and get extra
   * parameters from dependency management along the way.
   */
  def finalDependencies(
    from: Dependency,
    project: Project
  ): Seq[Dependency] = {

    // section numbers in the comments refer to withDependencyManagement

    val properties = project.properties.toMap

    val (_, configurations) = withParentConfigurations(
      if (from.configuration.isEmpty) defaultConfiguration else from.configuration,
      project.configurations
    )

    // Vague attempt at making the Maven scope model fit into the Ivy configuration one

    withExclusions(
      // 2.1 & 2.2
      depsWithDependencyManagement(
        // 1.7
        withProperties(project.dependencies, properties),
        withProperties(project.dependencyManagement, properties)
      ),
      from.exclusions
    )
    .flatMap {
      case (config0, dep0) =>
        // Dependencies from Maven verify
        //   dep.configuration.isEmpty
        // and expect dep.configuration to be filled here

        val dep =
          if (from.optional)
            dep0.copy(optional = true)
          else
            dep0

        val config = if (config0.isEmpty) defaultConfiguration else config0

        if (configurations(config))
          Seq(dep)
        else
          Nil
    }
  }

  /**
   * Default function checking whether a profile is active, given
   * its id, activation conditions, and the properties of its project.
   */
  def defaultProfileActivation(
    id: String,
    activation: Activation,
    props: Map[String, String]
  ): Boolean =
    activation.properties.nonEmpty &&
      activation.properties.forall {
        case (name, valueOpt) =>
          if (name.startsWith("!")) {
            props.get(name.drop(1)).isEmpty
          } else {
            props.get(name).exists { v =>
              valueOpt.forall { reqValue =>
                if (reqValue.startsWith("!"))
                  v != reqValue.drop(1)
                else
                  v == reqValue
              }
            }
          }
      }

  def userProfileActivation(userProfiles: Set[String])(
    id: String,
    activation: Activation,
    props: Map[String, String]
  ): Boolean =
    userProfiles(id) ||
      defaultProfileActivation(id, activation, props)

  /**
   * Default dependency filter used during resolution.
   *
   * Does not follow optional dependencies.
   */
  def defaultFilter(dep: Dependency): Boolean =
    !dep.optional

  val defaultTypes = Set[Type](Type.jar, Type.testJar, Type.bundle)

  def forceScalaVersion(sv: String): Dependency => Dependency = {

    val sbv = sv.split('.').take(2).mkString(".")

    val scalaModules = Set(
      ModuleName("scala-library"),
      ModuleName("scala-reflect"),
      ModuleName("scala-compiler"),
      ModuleName("scalap")
    )

    def fullCrossVersionBase(module: Module): Option[String] =
      if (module.attributes.isEmpty && !module.name.value.endsWith("_" + sv)) {
        val idx = module.name.value.lastIndexOf("_" + sbv + ".")
        if (idx < 0)
          None
        else {
          val lastPart = module.name.value.substring(idx + 1 + sbv.length + 1)
          if (lastPart.isEmpty || lastPart.exists(c => !c.isDigit)) // FIXME Not fine with -M5 or -RC1
            None
          else
            Some(module.name.value.substring(0, idx))
        }
      } else
        None

    dep =>
      if (dep.module.organization == Organization("org.scala-lang") && scalaModules.contains(dep.module.name))
        dep.copy(version = sv)
      else
        fullCrossVersionBase(dep.module) match {
          case Some(base) =>
            dep.copy(
              module = dep.module.copy(
                name = ModuleName(base + "_" + sv)
              )
            )
          case None =>
            dep
        }
  }

}


/**
 * State of a dependency resolution.
 *
 * Done if method `isDone` returns `true`.
 *
 * @param dependencies: current set of dependencies
 * @param conflicts: conflicting dependencies
 * @param projectCache: cache of known projects
 * @param errorCache: keeps track of the modules whose project definition could not be found
 */
final case class Resolution(
  rootDependencies: Seq[Dependency],
  dependencies: Set[Dependency],
  forceVersions: Map[Module, String],
  conflicts: Set[Dependency],
  projectCache: Map[Resolution.ModuleVersion, (Artifact.Source, Project)],
  errorCache: Map[Resolution.ModuleVersion, Seq[String]],
  finalDependenciesCache: Map[Dependency, Seq[Dependency]],
  filter: Option[Dependency => Boolean],
  osInfo: Activation.Os,
  jdkVersion: Option[Version],
  userActivations: Option[Map[String, Boolean]],
  mapDependencies: Option[Dependency => Dependency],
  forceProperties: Map[String, String]
) {

  def copyWithCache(
    rootDependencies: Seq[Dependency] = rootDependencies,
    dependencies: Set[Dependency] = dependencies,
    forceVersions: Map[Module, String] = forceVersions,
    conflicts: Set[Dependency] = conflicts,
    errorCache: Map[Resolution.ModuleVersion, Seq[String]] = errorCache,
    filter: Option[Dependency => Boolean] = filter,
    osInfo: Activation.Os = osInfo,
    jdkVersion: Option[Version] = jdkVersion,
    userActivations: Option[Map[String, Boolean]] = userActivations
    // don't allow changing mapDependencies here - that would invalidate finalDependenciesCache
    // don't allow changing projectCache here - use addToProjectCache that takes forceProperties into account
  ): Resolution =
    copy(
      rootDependencies,
      dependencies,
      forceVersions,
      conflicts,
      projectCache,
      errorCache,
      finalDependenciesCache ++ finalDependenciesCache0.asScala,
      filter,
      osInfo,
      jdkVersion,
      userActivations
    )

  def addToProjectCache(projects: (Resolution.ModuleVersion, (Artifact.Source, Project))*): Resolution = {

    val duplicates = projects
      .collect {
        case (modVer, _) if projectCache.contains(modVer) =>
          modVer
      }

    assert(duplicates.isEmpty, s"Projects already added in resolution: ${duplicates.mkString(", ")}")

    copy(
      finalDependenciesCache = finalDependenciesCache ++ finalDependenciesCache0.asScala,
      projectCache = projectCache ++ projects.map {
        case (modVer, (s, p)) =>
          val p0 = withDependencyManagement(p.copy(properties = p.properties.filter(kv => !forceProperties.contains(kv._1)) ++ forceProperties))
          (modVer, (s, p0))
      }
    )
  }

  import Resolution._

  private[core] val finalDependenciesCache0 = new ConcurrentHashMap[Dependency, Seq[Dependency]]

  private def finalDependencies0(dep: Dependency): Seq[Dependency] =
    if (dep.transitive) {
      val deps = finalDependenciesCache.getOrElse(dep, finalDependenciesCache0.get(dep))

      if (deps == null)
        projectCache.get(dep.moduleVersion) match {
          case Some((_, proj)) =>
            val res0 = finalDependencies(dep, proj).filter(filter getOrElse defaultFilter)
            val res = mapDependencies.fold(res0)(res0.map(_))
            finalDependenciesCache0.put(dep, res)
            res
          case None => Nil
        }
      else
        deps
    } else
      Nil

  def dependenciesOf(dep: Dependency, withReconciledVersions: Boolean = true): Seq[Dependency] =
    if (withReconciledVersions)
      finalDependencies0(dep).map { trDep =>
        trDep.copy(
          version = reconciledVersions.getOrElse(trDep.module, trDep.version)
        )
      }
    else
      finalDependencies0(dep)

  /**
   * Transitive dependencies of the current dependencies, according to
   * what there currently is in cache.
   *
   * No attempt is made to solve version conflicts here.
   */
  lazy val transitiveDependencies: Seq[Dependency] =
    (dependencies -- conflicts)
      .toVector
      .flatMap(finalDependencies0)

  /**
   * The "next" dependency set, made of the current dependencies and their
   * transitive dependencies, trying to solve version conflicts.
   * Transitive dependencies are calculated with the current cache.
   *
   * May contain dependencies added in previous iterations, but no more
   * required. These are filtered below, see `newDependencies`.
   *
   * Returns a tuple made of the conflicting dependencies, all
   * the dependencies, and the retained version of each module.
   */
  lazy val nextDependenciesAndConflicts: (Seq[Dependency], Seq[Dependency], Map[Module, String]) =
    // TODO Provide the modules whose version was forced by dependency overrides too
    merge(
      rootDependencies.map(withDefaultConfig) ++ dependencies ++ transitiveDependencies,
      forceVersions
    )

  def reconciledVersions: Map[Module, String] =
    nextDependenciesAndConflicts._3

  /**
   * The modules we miss some info about.
   */
  lazy val missingFromCache: Set[ModuleVersion] = {
    val modules = dependencies
      .map(_.moduleVersion)
    val nextModules = nextDependenciesAndConflicts._2
      .map(_.moduleVersion)

    (modules ++ nextModules)
      .filterNot(mod => projectCache.contains(mod) || errorCache.contains(mod))
  }


  /**
   * Whether the resolution is done.
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
    dep.copy(version = "")

  /**
   * Returns a map giving the dependencies that brought each of
   * the dependency of the "next" dependency set.
   *
   * The versions of all the dependencies returned are erased (emptied).
   */
  lazy val reverseDependencies: Map[Dependency, Vector[Dependency]] = {
    val (updatedConflicts, updatedDeps, _) = nextDependenciesAndConflicts

    val trDepsSeq =
      for {
        dep <- updatedDeps
        trDep <- finalDependencies0(dep)
      } yield eraseVersion(trDep) -> eraseVersion(dep)

    val knownDeps = (updatedDeps ++ updatedConflicts)
      .map(eraseVersion)
      .toSet

    trDepsSeq
      .groupBy(_._1)
      .mapValues(_.map(_._2).toVector)
      .filterKeys(knownDeps)
      .toVector.toMap // Eagerly evaluate filterKeys/mapValues
  }

  /**
   * Returns dependencies from the "next" dependency set, filtering out
   * those that are no more required.
   *
   * The versions of all the dependencies returned are erased (emptied).
   */
  lazy val remainingDependencies: Set[Dependency] = {
    val rootDependencies0 = rootDependencies
      .map(withDefaultConfig)
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
            .mapValues(broughtBy =>
              broughtBy
                .filter(x => remaining.contains(x) || rootDependencies0(x))
            )
            .toVector
            .toMap
        )
    }

    val filteredReverseDependencies = helper(reverseDependencies)

    rootDependencies0 ++ filteredReverseDependencies.keys
  }

  /**
   * The final next dependency set, stripped of no more required ones.
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
      dependencies = newDependencies ++ newConflicts,
      conflicts = newConflicts.toSet
    )
  }

  /**
   * If no module info is missing, the next state of the resolution,
   * which can be immediately calculated. Else, the current resolution.
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
    } else
      this
  }

  /**
   * Required modules for the dependency management of `project`.
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

      val parentProperties0 = project
        .parent
        .flatMap(projectCache.get)
        .map(_._2.properties.toMap)
        .getOrElse(Map())

      val approxProperties = parentProperties0 ++ projectProperties(project)

      val profileDependencies =
        profiles(
          project,
          approxProperties,
          osInfo,
          jdkVersion,
          userActivations
        ).flatMap(p => p.dependencies ++ p.dependencyManagement)

      val modules = withProperties(
        project.dependencies ++ project.dependencyManagement ++ profileDependencies,
        approxProperties
      ).collect {
        case (Configuration.`import`, dep) => dep.moduleVersion
      }

      modules.toSet
    }
  }

  /**
   * Missing modules in cache, to get the full list of dependencies of
   * `project`, taking dependency management / inheritance into account.
   *
   * Note that adding the missing modules to the cache may unveil other
   * missing modules, so these modules should be added to the cache, and
   * `dependencyManagementMissing` checked again for new missing modules.
   */
  def dependencyManagementMissing(project: Project): Set[ModuleVersion] = {

    @tailrec
    def helper(
      toCheck: Set[ModuleVersion],
      done: Set[ModuleVersion],
      missing: Set[ModuleVersion]
    ): Set[ModuleVersion] = {

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
      } else if (toCheck.exists(errorCache.contains)) {
        val (errored, remaining) = toCheck.partition(errorCache.contains)
        helper(remaining, done ++ errored, missing)
      } else
        helper(Set.empty, done, missing ++ toCheck)
    }

    helper(
      dependencyManagementRequirements(project),
      Set(project.moduleVersion),
      Set.empty
    )
  }

  private def withFinalProperties(project: Project): Project =
    project.copy(
      properties = projectProperties(project)
    )

  /**
   * Add dependency management / inheritance related items to `project`,
   * from what's available in cache.
   *
   * It is recommended to have fetched what `dependencyManagementMissing`
   * returned prior to calling this.
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

    val parentProperties0 = project
      .parent
      .flatMap(projectCache.get)
      .map(_._2.properties)
      .getOrElse(Seq())

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
    val project0 = withFinalProperties(
      project.copy(
        properties = parentProperties0 ++ project.properties ++ profiles0.flatMap(_.properties) // belongs to 1.5 & 1.6
      )
    )

    val propertiesMap0 = project0.properties.toMap

    val dependencies0 = addDependencies(
      (project0.dependencies +: profiles0.map(_.dependencies)).map(withProperties(_, propertiesMap0))
    )
    val dependenciesMgmt0 = addDependencies(
      (project0.dependencyManagement +: profiles0.map(_.dependencyManagement)).map(withProperties(_, propertiesMap0))
    )

    val deps0 =
      dependencies0.collect {
        case (Configuration.`import`, dep) =>
          dep.moduleVersion
      } ++
      dependenciesMgmt0.collect {
        case (Configuration.`import`, dep) =>
          dep.moduleVersion
      } ++
      project0.parent // belongs to 1.5 & 1.6

    val deps = deps0.filter(projectCache.contains)

    val projs = deps
      .map(projectCache(_)._2)

    val depMgmt = (
      project0.dependencyManagement +: (
        profiles0.map(_.dependencyManagement) ++
        projs.map(_.dependencyManagement)
      )
    )
      .map(withProperties(_, propertiesMap0))
      .foldLeft(Map.empty[DepMgmt.Key, (Configuration, Dependency)])(DepMgmt.addSeq)

    val depsSet = deps.toSet

    project0.copy(
      version = substituteProps(project0.version, propertiesMap0),
      dependencies =
        dependencies0
          .filterNot{case (config, dep) =>
            config == Configuration.`import` && depsSet(dep.moduleVersion)
          } ++
        project0.parent  // belongs to 1.5 & 1.6
          .filter(projectCache.contains)
          .toSeq
          .flatMap(projectCache(_)._2.dependencies),
      dependencyManagement = depMgmt.values.toSeq
        .filterNot{case (config, dep) =>
          config == Configuration.`import` && depsSet(dep.moduleVersion)
        }
    )
  }

  /**
    * Minimized dependency set. Returns `dependencies` with no redundancy.
    *
    * E.g. `dependencies` may contains several dependencies towards module org:name:version,
    * a first one excluding A and B, and a second one excluding A and C. In practice, B and C will
    * be brought anyway, because the first dependency doesn't exclude C, and the second one doesn't
    * exclude B. So having both dependencies is equivalent to having only one dependency towards
    * org:name:version, excluding just A.
    *
    * The same kind of substitution / filtering out can be applied with configurations. If
    * `dependencies` contains several dependencies towards org:name:version, a first one bringing
    * its configuration "runtime", a second one "compile", and the configuration mapping of
    * org:name:version says that "runtime" extends "compile", then all the dependencies brought
    * by the latter will be brought anyway by the former, so that the latter can be removed.
    *
    * @return A minimized `dependencies`, applying this kind of substitutions.
    */
  def minDependencies: Set[Dependency] =
    Orders.minDependencies(
      dependencies,
      dep =>
        projectCache
          .get(dep)
          .map(_._2.configurations)
          .getOrElse(Map.empty)
    )

  def artifacts(types: Set[Type] = defaultTypes, classifiers: Option[Seq[Classifier]] = None): Seq[Artifact] =
    dependencyArtifacts(classifiers)
      .collect {
        case (_, attr, artifact) if types(attr.`type`) =>
          artifact
      }
      .distinct

  def dependencyArtifacts(classifiers: Option[Seq[Classifier]] = None): Seq[(Dependency, Attributes, Artifact)] =
    for {
      dep <- minDependencies.toSeq
      (source, proj) <- projectCache
        .get(dep.moduleVersion)
        .toSeq

      classifiers0 =
        if (dep.attributes.classifier.isEmpty)
          classifiers
        else
          Some(classifiers.getOrElse(Nil) ++ Seq(dep.attributes.classifier))

      (attributes, artifact) <- source.artifacts(dep, proj, classifiers0)
    } yield (dep, attributes, artifact)


  @deprecated("Use the artifacts overload accepting types and classifiers instead", "1.1.0-M8")
  def classifiersArtifacts(classifiers: Seq[String]): Seq[Artifact] =
    artifacts(classifiers = Some(classifiers.map(Classifier(_))))

  @deprecated("Use artifacts overload accepting types and classifiers instead", "1.1.0-M8")
  def artifacts: Seq[Artifact] =
    artifacts()

  @deprecated("Use artifacts overload accepting types and classifiers instead", "1.1.0-M8")
  def artifacts(withOptional: Boolean): Seq[Artifact] =
    artifacts()

  @deprecated("Use dependencyArtifacts overload accepting classifiers instead", "1.1.0-M8")
  def dependencyArtifacts: Seq[(Dependency, Artifact)] =
    dependencyArtifacts(None).map(t => (t._1, t._3))

  @deprecated("Use dependencyArtifacts overload accepting classifiers instead", "1.1.0-M8")
  def dependencyArtifacts(withOptional: Boolean): Seq[(Dependency, Artifact)] =
    dependencyArtifacts().map(t => (t._1, t._3))

  @deprecated("Use dependencyArtifacts overload accepting classifiers instead", "1.1.0-M8")
  def dependencyClassifiersArtifacts(classifiers: Seq[String]): Seq[(Dependency, Artifact)] =
    dependencyArtifacts(Some(classifiers.map(Classifier(_)))).map(t => (t._1, t._3))


  /**
    * Returns errors on dependencies
    * @return errors
    */
  def errors: Seq[(ModuleVersion, Seq[String])] = errorCache.toSeq

  @deprecated("Use errors instead", "1.1.0")
  def metadataErrors: Seq[(ModuleVersion, Seq[String])] = errors

  def dependenciesWithSelectedVersions: Set[Dependency] =
    dependencies.map { dep =>
      reconciledVersions.get(dep.module).fold(dep) { v =>
        dep.copy(version = v)
      }
    }

  /**
    * Removes from this `Resolution` dependencies that are not in `dependencies` neither brought
    * transitively by them.
    *
    * This keeps the versions calculated by this `Resolution`. The common dependencies of different
    * subsets will thus be guaranteed to have the same versions.
    *
    * @param dependencies: the dependencies to keep from this `Resolution`
    */
  def subset(dependencies: Seq[Dependency]): Resolution = {

    def updateVersion(dep: Dependency): Dependency =
      dep.copy(version = reconciledVersions.getOrElse(dep.module, dep.version))

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

    copyWithCache(
      rootDependencies = dependencies,
      dependencies = helper(dependencies.map(updateVersion).toSet)
      // don't know if something should be done about conflicts
    )
  }
}
