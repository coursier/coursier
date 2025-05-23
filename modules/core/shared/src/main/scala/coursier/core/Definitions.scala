package coursier.core

import java.util.concurrent.ConcurrentMap

import coursier.core.Validation._
import coursier.error.VariantError
import coursier.util.Artifact
import coursier.version.{Version => Version0}
import dataclass.data

final case class Organization(value: String) extends AnyVal {
  def map(f: String => String): Organization =
    Organization(f(value))
}

object Organization {
  implicit val ordering: Ordering[Organization] =
    Ordering[String].on(_.value)
}

final case class ModuleName(value: String) extends AnyVal {
  def map(f: String => String): ModuleName =
    ModuleName(f(value))
}

object ModuleName {
  implicit val ordering: Ordering[ModuleName] =
    Ordering[String].on(_.value)
}

/** Identifies a "module".
  *
  * During resolution, all dependencies having the same module will be given the same version, if
  * there are no version conflicts between them.
  *
  * Using the same terminology as Ivy.
  */
@data(apply = false, settersCallApply = true) class Module(
  organization: Organization,
  name: ModuleName,
  attributes: Map[String, String]
) {
  assertValid(organization.value, "organization")
  assertValid(name.value, "module name")

  def trim: Module = copy(
    organization.map(_.trim),
    name.map(_.trim)
  )

  private def attributesStr = attributes.toSeq
    .sortBy { case (k, _) => k }
    .map { case (k, v) => s"$k=$v" }
    .mkString(";")

  def nameWithAttributes: String =
    name.value + (if (attributes.nonEmpty) s";$attributesStr" else "")

  def repr: String =
    s"${organization.value}:$nameWithAttributes"

  override def toString: String =
    repr

  def orgName: String =
    s"${organization.value}:${name.value}"

  lazy val hasProperties =
    organization.value.contains("$") ||
    name.value.contains("$") ||
    attributes.exists {
      case (k, v) =>
        k.contains("$") || v.contains("$")
    }

  final override lazy val hashCode = tuple.hashCode()

  private[core] def copy(
    organization: Organization = this.organization,
    name: ModuleName = this.name,
    attributes: Map[String, String] = this.attributes
  ) = Module(organization, name, attributes)
}

object Module {

  private[core] val instanceCache: ConcurrentMap[Module, Module] =
    coursier.util.Cache.createCache()

  def apply(organization: Organization, name: ModuleName, attributes: Map[String, String]): Module =
    coursier.util.Cache.cacheMethod(instanceCache)(new Module(organization, name, attributes))
}

final case class Type(value: String) extends AnyVal {
  def isEmpty: Boolean =
    value.isEmpty
  def nonEmpty: Boolean =
    value.nonEmpty
  def map(f: String => String): Type =
    Type(f(value))

  def asExtension: Extension =
    Extension(value)
}

object Type {
  implicit val ordering: Ordering[Type] =
    Ordering[String].on(_.value)

  val jar     = Type("jar")
  val testJar = Type("test-jar")
  val bundle  = Type("bundle")
  val doc     = Type("doc")
  val source  = Type("src")

  // Typo for doc and src ???
  val javadoc    = Type("javadoc")
  val javaSource = Type("java-source")

  val ivy = Type("ivy")
  val pom = Type("pom")

  val empty = Type("")
  val all   = Type("*")

  object Exotic {
    // https://github.com/sbt/sbt/blob/47cd001eea8ef42b7c1db9ffdf48bec16b8f733b/main/src/main/scala/sbt/Defaults.scala#L227
    val mavenPlugin = Type("maven-plugin")

    // https://github.com/sbt/librarymanagement/blob/bb2c73e183fa52e2fb4b9ae7aca55799f3ff6624/ivy/src/main/scala/sbt/internal/librarymanagement/CustomPomParser.scala#L79
    val eclipsePlugin = Type("eclipse-plugin")
    val hk2           = Type("hk2-jar")
    val orbit         = Type("orbit")
    val scalaJar      = Type("scala-jar")

    // Kotlin stuff
    val klib = Type("klib")

    // Android thing
    val aar = Type("aar")
  }
}

final case class Classifier(value: String) extends AnyVal {
  def isEmpty: Boolean =
    value.isEmpty
  def nonEmpty: Boolean =
    value.nonEmpty
  def map(f: String => String): Classifier =
    Classifier(f(value))
}

object Classifier {
  implicit val ordering: Ordering[Classifier] =
    Ordering[String].on(_.value)

  val empty = Classifier("")

  val tests   = Classifier("tests")
  val javadoc = Classifier("javadoc")
  val sources = Classifier("sources")
}

final case class Extension(value: String) extends AnyVal {
  def isEmpty: Boolean =
    value.isEmpty
  def map(f: String => String): Extension =
    Extension(f(value))

  def asType: Type =
    Type(value)
}

object Extension {
  implicit val ordering: Ordering[Extension] =
    Ordering[String].on(_.value)

  val jar = Extension("jar")
  val pom = Extension("pom")

  val empty = Extension("")
}

final case class Configuration(value: String) extends AnyVal {
  def isEmpty: Boolean =
    value.isEmpty
  def nonEmpty: Boolean =
    value.nonEmpty
  def -->(target: Configuration): Configuration =
    Configuration(s"$value->${target.value}")
  def map(f: String => String): Configuration =
    Configuration(f(value))
}

object Configuration {
  implicit val ordering: Ordering[Configuration] =
    Ordering[String].on(_.value)

  val empty = Configuration("")

  val compile = Configuration("compile")
  val runtime = Configuration("runtime")
  val test    = Configuration("test")

  val default        = Configuration("default")
  val defaultCompile = Configuration("default(compile)")
  val defaultRuntime = Configuration("default(runtime)")

  val provided = Configuration("provided")
  val `import` = Configuration("import")
  val optional = Configuration("optional")

  val all = Configuration("*")

  def join(confs: Configuration*): Configuration =
    Configuration(confs.map(_.value).mkString(";"))
}

@data class Attributes(
  `type`: Type,
  classifier: Classifier
) {
  def packaging: Type =
    if (`type`.isEmpty)
      Type.jar
    else
      `type`

  def packagingAndClassifier: String =
    if (isEmpty)
      ""
    else if (classifier.isEmpty)
      packaging.value
    else
      s"${packaging.value}:${classifier.value}"

  def publication(name: String, ext: Extension): Publication =
    Publication(name, `type`, ext, classifier)

  def isEmpty: Boolean =
    `type`.isEmpty && classifier.isEmpty

  def normalize: Attributes =
    if (`type` == Type.jar) withType(Type.empty)
    else this
}

object Attributes {
  val empty = Attributes(Type.empty, Classifier.empty)
}

@data class Project(
  module: Module,
  version0: Version0,
  dependencies0: Seq[(Variant, Dependency)],
  // For Maven, this is the standard scopes as an Ivy configuration
  configurations: Map[Configuration, Seq[Configuration]],

  // Maven-specific
  parent0: Option[(Module, Version0)],
  dependencyManagement0: Seq[(Variant, Dependency)],
  properties: Seq[(String, String)],
  profiles: Seq[Profile],
  versions: Option[Versions],
  snapshotVersioning: Option[SnapshotVersioning],
  packagingOpt: Option[Type],
  relocated: Boolean,
  /** Optional exact version used to get this project metadata. May not match `version` for projects
    * having a wrong version in their metadata.
    */
  actualVersionOpt0: Option[Version0],
  publications0: Seq[(Variant, Publication)],

  // Extra infos, not used during resolution
  info: Info,
  overrides: Overrides,
  variants: Map[Variant.Attributes, Map[String, String]],
  variantPublications: Map[Variant.Attributes, Seq[VariantPublication]]
) {

  @deprecated("Use dependencies0 instead", "2.1.25")
  def dependencies: Seq[(Configuration, Dependency)] =
    dependencies0.map {
      case (c: Variant.Configuration, dep) =>
        (c.configuration, dep)
      case (_: Variant.Attributes, _) =>
        sys.error("Deprecated method doesn't support Gradle Module variant attributes")
    }
  @deprecated("Use withDependencies0 instead", "2.1.25")
  def withDependencies(newDependencies: Seq[(Configuration, Dependency)]): Project =
    withDependencies0(
      newDependencies.map {
        case (config, dep) =>
          (Variant.Configuration(config), dep)
      }
    )

  @deprecated("Use publications0 instead", "2.1.25")
  def publications: Seq[(Configuration, Publication)] =
    publications0.map {
      case (c: Variant.Configuration, pub) =>
        (c.configuration, pub)
      case (_: Variant.Attributes, _) =>
        sys.error("Deprecated method doesn't support Gradle Module variant attributes")
    }
  @deprecated("Use withPublications0 instead", "2.1.25")
  def withPublications(newPublications: Seq[(Configuration, Publication)]): Project =
    withPublications0(
      newPublications.map {
        case (config, pub) =>
          (Variant.Configuration(config), pub)
      }
    )

  @deprecated("Use the override accepting Version-s instead", "2.1.25")
  def this(
    module: Module,
    version: String,
    dependencies: Seq[(Configuration, Dependency)],
    configurations: Map[Configuration, Seq[Configuration]],
    parent: Option[(Module, String)],
    dependencyManagement: Seq[(Configuration, Dependency)],
    properties: Seq[(String, String)],
    profiles: Seq[Profile],
    versions: Option[Versions],
    snapshotVersioning: Option[SnapshotVersioning],
    packagingOpt: Option[Type],
    relocated: Boolean,
    actualVersionOpt: Option[String],
    publications: Seq[(Configuration, Publication)],
    info: Info,
    overrides: Overrides
  ) =
    this(
      module,
      Version0(version),
      dependencies.map {
        case (config, dep) =>
          (Variant.Configuration(config), dep)
      },
      configurations,
      parent.map { case (mod, ver) => (mod, Version0(ver)) },
      dependencyManagement.map {
        case (config, dep) =>
          (Variant.Configuration(config), dep)
      },
      properties,
      profiles,
      versions,
      snapshotVersioning,
      packagingOpt,
      relocated,
      actualVersionOpt.map(Version0(_)),
      publications.map {
        case (config, dep) =>
          (Variant.Configuration(config), dep)
      },
      info,
      overrides,
      Map.empty,
      Map.empty
    )

  @deprecated("Use the override accepting Version-s instead", "2.1.25")
  def this(
    module: Module,
    version: String,
    dependencies: Seq[(Configuration, Dependency)],
    configurations: Map[Configuration, Seq[Configuration]],
    parent: Option[(Module, String)],
    dependencyManagement: Seq[(Configuration, Dependency)],
    properties: Seq[(String, String)],
    profiles: Seq[Profile],
    versions: Option[Versions],
    snapshotVersioning: Option[SnapshotVersioning],
    packagingOpt: Option[Type],
    relocated: Boolean,
    actualVersionOpt: Option[String],
    publications: Seq[(Configuration, Publication)],
    info: Info
  ) =
    this(
      module,
      Version0(version),
      dependencies.map {
        case (config, dep) =>
          (Variant.Configuration(config), dep)
      },
      configurations,
      parent.map { case (mod, ver) => (mod, Version0(ver)) },
      dependencyManagement.map {
        case (config, dep) =>
          (Variant.Configuration(config), dep)
      },
      properties,
      profiles,
      versions,
      snapshotVersioning,
      packagingOpt,
      relocated,
      actualVersionOpt.map(Version0(_)),
      publications.map {
        case (config, dep) =>
          (Variant.Configuration(config), dep)
      },
      info,
      Overrides.empty,
      Map.empty,
      Map.empty
    )

  @deprecated("Use moduleVersion0 instead", "2.1.25")
  lazy val moduleVersion: (Module, String) = (module, version)

  lazy val moduleVersion0: (Module, Version0) = (module, version0)

  @deprecated("Use version0 instead", "2.1.25")
  def version: String = version0.repr
  @deprecated("Use withVersion0 instead", "2.1.25")
  def withVersion(newVersion: String): Project =
    if (version == newVersion) this
    else withVersion0(Version0(newVersion))

  @deprecated("Use parent0 instead", "2.1.25")
  def parent: Option[(Module, String)] =
    parent0.map {
      case (mod, ver) =>
        (mod, ver.asString)
    }
  @deprecated("Use withParent0 instead", "2.1.25")
  def withParent(newParent: Option[(Module, String)]): Project =
    withParent0(
      newParent.map {
        case (mod, ver) =>
          (mod, Version0(ver))
      }
    )

  @deprecated("Use actualVersionOpt0 instead", "2.1.25")
  def actualVersionOpt: Option[String] =
    actualVersionOpt0.map(_.asString)
  @deprecated("Use withActualVersionOpt0 instead", "2.1.25")
  def withActualVersionOpt(newParent: Option[String]): Project =
    withActualVersionOpt0(newParent.map(Version0(_)))

  @deprecated("Use dependencyManagement0 instead", "2.1.25")
  def dependencyManagement: Seq[(Configuration, Dependency)] =
    dependencyManagement0.map {
      case (c: Variant.Configuration, dep) =>
        (c.configuration, dep)
      case (_: Variant.Attributes, _) =>
        sys.error("Deprecated method doesn't support Gradle Module variant attributes")
    }
  @deprecated("Use withDependencyManagement0 instead", "2.1.25")
  def withDependencyManagement(dependencyManagement: Seq[(Configuration, Dependency)]): Project =
    withDependencyManagement0(
      dependencyManagement.map {
        case (c, dep) =>
          (Variant.Configuration(c), dep)
      }
    )

  /** All configurations that each configuration extends, including the ones it extends transitively
    */
  lazy val allConfigurations: Map[Configuration, Set[Configuration]] =
    Orders.allConfigurations0(configurations)

  /** Version used to get this project metadata if available, else the version from metadata. May
    * not match `version` for projects having a wrong version in their metadata, if the actual
    * version was kept around.
    */
  def actualVersion0: Version0 = actualVersionOpt0.getOrElse(version0)

  @deprecated("Use actualVersion0 instead", "2.1.25")
  def actualVersion: String = actualVersion0.asString

  def variantFor(attr: VariantSelector.AttributesBased)
    : Either[VariantError, Variant.Attributes] = {
    def retainedVariantsFor(attr0: VariantSelector.AttributesBased) = variants
      .iterator
      .map {
        case (name, values) =>
          (name, attr0.matches(values))
      }
      .collect {
        case (name, Some(score)) =>
          (name, score)
      }
      .toVector
    val baseRetainedVariants = retainedVariantsFor(attr)
    def isModuleBasedBom =
      dependencies0.isEmpty && (dependencyManagement0.nonEmpty || !overrides.isEmpty) &&
      variants.nonEmpty
    val (actualAttr, retainedVariants) =
      if (
        baseRetainedVariants.isEmpty &&
        isModuleBasedBom &&
        attr.matchers.get("org.gradle.category")
          .contains(VariantSelector.VariantMatcher.Library)
      ) {
        val attr0 =
          attr.addAttributes("org.gradle.category" -> VariantSelector.VariantMatcher.Platform)
        (attr0, retainedVariantsFor(attr0))
      }
      else
        (attr, baseRetainedVariants)
    if (retainedVariants.isEmpty)
      Left(
        new VariantError.NoVariantFound(
          module,
          actualVersion0,
          actualAttr,
          variants.toVector.sortBy(_._1.variantName)
        )
      )
    else {
      val highestScore = retainedVariants.map(_._2).max
      val retainedVariants0 = retainedVariants.collect {
        case (name, `highestScore`) => name
      }
      assert(retainedVariants0.nonEmpty)
      if (retainedVariants0.lengthCompare(1) == 0)
        Right(retainedVariants0.head)
      else {
        val retainedVariantsSet = retainedVariants0.toSet
        Left(
          new VariantError.FoundTooManyVariants(
            module,
            actualVersion0,
            actualAttr,
            variants
              .filter {
                case (k, v) =>
                  retainedVariantsSet.contains(k)
              }
              .toVector
              .sortBy(_._1.variantName)
          )
        )
      }
    }
  }

  def isRelocatedVariant(variant: Variant.Attributes): Option[Dependency] = {
    lazy val firstDeps = dependencies0.iterator.filter(_._1 == variant).take(2).toVector
    val isRelocated = variants.get(variant).exists(_.get("$relocated").contains("true")) &&
      firstDeps.length == 1
    if (isRelocated) Some(firstDeps.head._2)
    else None
  }

  lazy val depMgmtEquivalentConfigurations = variants.flatMap {
    case (attr, map) =>
      val attr0 = VariantSelector.AttributesBased(
        map
          .map {
            case (k, v) =>
              VariantSelector.VariantMatcher.fromString(k, v)
          }
          .map {
            case ("org.gradle.category", VariantSelector.VariantMatcher.Platform) =>
              ("org.gradle.category", VariantSelector.VariantMatcher.Library)
            case other =>
              other
          }
      )
      attr0.equivalentConfiguration.toSeq.map(attr -> _)
  }

  final override lazy val hashCode = tuple.hashCode
}

object Project {

  @deprecated("Use the override accepting Version-s instead", "2.1.25")
  def apply(
    module: Module,
    version: String,
    dependencies: Seq[(Configuration, Dependency)],
    configurations: Map[Configuration, Seq[Configuration]],
    parent: Option[(Module, String)],
    dependencyManagement: Seq[(Configuration, Dependency)],
    properties: Seq[(String, String)],
    profiles: Seq[Profile],
    versions: Option[Versions],
    snapshotVersioning: Option[SnapshotVersioning],
    packagingOpt: Option[Type],
    relocated: Boolean,
    actualVersionOpt: Option[String],
    publications: Seq[(Configuration, Publication)],
    info: Info
  ): Project =
    apply(
      module,
      Version0(version),
      dependencies.map {
        case (config, dep) =>
          (Variant.Configuration(config), dep)
      },
      configurations,
      parent.map { case (mod, ver) => (mod, Version0(ver)) },
      dependencyManagement.map {
        case (config, dep) =>
          (Variant.Configuration(config), dep)
      },
      properties,
      profiles,
      versions,
      snapshotVersioning,
      packagingOpt,
      relocated,
      actualVersionOpt.map(Version0(_)),
      publications.map {
        case (config, dep) =>
          (Variant.Configuration(config), dep)
      },
      info,
      Overrides.empty,
      Map.empty,
      Map.empty
    )
  @deprecated("Use the override accepting Version-s instead", "2.1.25")
  def apply(
    module: Module,
    version: String,
    dependencies: Seq[(Configuration, Dependency)],
    configurations: Map[Configuration, Seq[Configuration]],
    parent: Option[(Module, String)],
    dependencyManagement: Seq[(Configuration, Dependency)],
    properties: Seq[(String, String)],
    profiles: Seq[Profile],
    versions: Option[Versions],
    snapshotVersioning: Option[SnapshotVersioning],
    packagingOpt: Option[Type],
    relocated: Boolean,
    actualVersionOpt: Option[String],
    publications: Seq[(Configuration, Publication)],
    info: Info,
    overrides: Overrides
  ): Project =
    apply(
      module,
      Version0(version),
      dependencies.map {
        case (config, dep) =>
          (Variant.Configuration(config), dep)
      },
      configurations,
      parent.map { case (mod, ver) => (mod, Version0(ver)) },
      dependencyManagement.map {
        case (config, dep) =>
          (Variant.Configuration(config), dep)
      },
      properties,
      profiles,
      versions,
      snapshotVersioning,
      packagingOpt,
      relocated,
      actualVersionOpt.map(Version0(_)),
      publications.map {
        case (config, dep) =>
          (Variant.Configuration(config), dep)
      },
      info,
      overrides,
      Map.empty,
      Map.empty
    )
}

/** Extra project info, not used during resolution */
@data class Info(
  description: String,
  homePage: String,
  developers: Seq[Info.Developer],
  publication: Option[Versions.DateTime],
  scm: Option[Info.Scm],
  licenseInfo: Seq[Info.License]
) {
  def licenses: Seq[(String, Option[String])] = licenseInfo.map(li => li.name -> li.url)

  def withLicenses(license: Seq[(String, Option[String])]) =
    new Info(
      description,
      homePage,
      developers,
      publication,
      scm,
      license.map(l => Info.License(l._1, l._2, None, None))
    )

  def this(
    description: String,
    homePage: String,
    licenses: Seq[(String, Option[String])],
    developers: Seq[Info.Developer],
    publication: Option[Versions.DateTime],
    scm: Option[Info.Scm]
  ) =
    this(
      description,
      homePage,
      developers,
      publication,
      scm,
      licenses.map(l => Info.License(l._1, l._2, None, None))
    )
}

object Info {
  def apply(
    description: String,
    homePage: String,
    licenses: Seq[(String, Option[String])],
    developers: Seq[Info.Developer],
    publication: Option[Versions.DateTime],
    scm: Option[Info.Scm]
  ): Info = new Info(
    description,
    homePage,
    developers,
    publication,
    scm,
    licenseInfo = licenses.map(l => License(l._1, l._2, None, None))
  )

  @data class Developer(
    id: String,
    name: String,
    url: String
  )

  @data class Scm(
    url: Option[String],
    connection: Option[String],
    developerConnection: Option[String]
  )

  @data class License(
    name: String,
    url: Option[String],
    distribution: Option[String], // Maven-specific
    comments: Option[String]      // Maven-specific
  )

  val empty = Info("", "", Nil, None, None, Nil)
}

// Maven-specific
@data class Profile(
  id: String,
  activeByDefault: Option[Boolean],
  activation: Activation,
  dependencies: Seq[(Configuration, Dependency)],
  dependencyManagement: Seq[(Configuration, Dependency)],
  properties: Map[String, String]
)

// Maven-specific
@data class SnapshotVersion(
  classifier: Classifier,
  extension: Extension,
  value0: Version0,
  updated: Option[Versions.DateTime]
) {
  @deprecated("Use the override accepting a Version instead", "2.1.25")
  def this(
    classifier: Classifier,
    extension: Extension,
    value: String,
    updated: Option[Versions.DateTime]
  ) =
    this(
      classifier,
      extension,
      Version0(value),
      updated
    )

  @deprecated("Use value0 instead", "2.1.25")
  def value: String =
    value0.asString
  @deprecated("Use withValue0 instead", "2.1.25")
  def withValue(newValue: String): SnapshotVersion =
    if (newValue == value) this
    else withValue0(Version0(newValue))
}

object SnapshotVersion {
  @deprecated("Use the override accepting a Version instead", "2.1.25")
  def apply(
    classifier: Classifier,
    extension: Extension,
    value: String,
    updated: Option[Versions.DateTime]
  ): SnapshotVersion =
    SnapshotVersion(
      classifier,
      extension,
      Version0(value),
      updated
    )
}

// Maven-specific
@data class SnapshotVersioning(
  module: Module,
  version0: Version0,
  latest0: Version0,
  release0: Version0,
  timestamp: String,
  buildNumber: Option[Int],
  localCopy: Option[Boolean],
  lastUpdated: Option[Versions.DateTime],
  snapshotVersions: Seq[SnapshotVersion]
) {
  @deprecated("Use the override accepting Version-s instead", "2.1.25")
  def this(
    module: Module,
    version: String,
    latest: String,
    release: String,
    timestamp: String,
    buildNumber: Option[Int],
    localCopy: Option[Boolean],
    lastUpdated: Option[Versions.DateTime],
    snapshotVersions: Seq[SnapshotVersion]
  ) =
    this(
      module,
      Version0(version),
      Version0(latest),
      Version0(release),
      timestamp,
      buildNumber,
      localCopy,
      lastUpdated,
      snapshotVersions
    )

  @deprecated("Use version0 instead", "2.1.25")
  def version: String = version0.asString
  @deprecated("Use latest0 instead", "2.1.25")
  def latest: String = latest0.asString
  @deprecated("Use release0 instead", "2.1.25")
  def release: String = release0.asString

  @deprecated("Use withVersion0 instead", "2.1.25")
  def withVersion(newVersion: String): SnapshotVersioning =
    if (newVersion == version) this
    else withVersion0(Version0(newVersion))
  @deprecated("Use withLatest0 instead", "2.1.25")
  def withLatest(newLatest: String): SnapshotVersioning =
    if (newLatest == latest) this
    else withLatest0(Version0(newLatest))
  @deprecated("Use withRelease0 instead", "2.1.25")
  def withRelease(newRelease: String): SnapshotVersioning =
    if (newRelease == release) this
    else withRelease0(Version0(newRelease))
}

object SnapshotVersioning {
  @deprecated("Use the override accepting Version-s instead", "2.1.25")
  def apply(
    module: Module,
    version: String,
    latest: String,
    release: String,
    timestamp: String,
    buildNumber: Option[Int],
    localCopy: Option[Boolean],
    lastUpdated: Option[Versions.DateTime],
    snapshotVersions: Seq[SnapshotVersion]
  ): SnapshotVersioning =
    apply(
      module,
      Version0(version),
      Version0(latest),
      Version0(release),
      timestamp,
      buildNumber,
      localCopy,
      lastUpdated,
      snapshotVersions
    )
}

@data(apply = false, settersCallApply = true) class Publication(
  name: String,
  `type`: Type,
  ext: Extension,
  classifier: Classifier
) {
  def attributes: Attributes = Attributes(`type`, classifier)
  def isEmpty: Boolean =
    name.isEmpty && `type`.isEmpty && ext.isEmpty && classifier.isEmpty

  lazy val attributesHaveProperties =
    `type`.value.contains("$") ||
    classifier.value.contains("$")

  final override lazy val hashCode = tuple.hashCode
}

object Publication {
  private[core] val instanceCache: ConcurrentMap[Publication, Publication] =
    coursier.util.Cache.createCache()

  def apply(name: String, `type`: Type, ext: Extension, classifier: Classifier): Publication =
    coursier.util.Cache.cacheMethod(instanceCache)(new Publication(name, `type`, ext, classifier))

  val empty: Publication =
    Publication("", Type.empty, Extension.empty, Classifier.empty)
}

@data class VariantPublication(
  name: String,
  url: String
)

trait ArtifactSource {
  def artifacts(
    dependency: Dependency,
    project: Project,
    overrideClassifiers: Option[Seq[Classifier]]
  ): Seq[(Publication, Artifact)]
}

object ArtifactSource {
  trait ModuleBased {
    def moduleArtifacts(
      dependency: Dependency,
      project: Project,
      overrideAttributes: Option[VariantSelector.AttributesBased]
    ): Seq[(VariantPublication, Artifact)]
  }
}

private[coursier] object Validation {
  def validateCoordinate(value: String, name: String): Either[String, String] =
    Seq('/', '\\').foldLeft[Either[String, String]](Right(value)) { (acc, char) =>
      acc.filterOrElse(value => !value.contains(char), s"$name $value contains invalid '$char'")
    }

  def assertValid(value: String, name: String): Unit =
    validateCoordinate(value, name).fold(msg => throw new AssertionError(msg), identity)
}
