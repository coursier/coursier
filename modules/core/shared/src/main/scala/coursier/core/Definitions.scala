package coursier.core

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

/**
 * Identifies a "module".
 *
 * During resolution, all dependencies having the same module
 * will be given the same version, if there are no version conflicts
 * between them.
 *
 * Using the same terminology as Ivy.
 */
final case class Module(
  organization: Organization,
  name: ModuleName,
  attributes: Map[String, String]
) {

  def trim: Module = copy(
    organization = organization.map(_.trim),
    name = name.map(_.trim)
  )

  private def attributesStr = attributes.toSeq
    .sortBy { case (k, _) => k }
    .map { case (k, v) => s"$k=$v" }
    .mkString(";")

  def nameWithAttributes: String =
    name.value + (if (attributes.nonEmpty) s";$attributesStr" else "")

  override def toString: String =
    s"${organization.value}:$nameWithAttributes"

  def orgName: String =
    s"${organization.value}:${name.value}"

  override final lazy val hashCode = Module.unapply(this).get.hashCode()
}

/**
 * Dependencies with the same @module will typically see their @version-s merged.
 *
 * The remaining fields are left untouched, some being transitively
 * propagated (exclusions, optional, in particular).
 */
final case class Dependency(
  module: Module,
  version: String,
  configuration: String,
  exclusions: Set[(Organization, ModuleName)],

  // Maven-specific
  attributes: Attributes,
  optional: Boolean,

  transitive: Boolean
) {
  lazy val moduleVersion = (module, version)

  override lazy val hashCode = Dependency.unapply(this).get.hashCode()

  def mavenPrefix: String = {
    if (attributes.isEmpty)
      module.orgName
    else {
      s"${module.orgName}:${attributes.packagingAndClassifier}"
    }
  }
}

final case class Type(value: String) extends AnyVal {
  def isEmpty: Boolean =
    value.isEmpty
  def nonEmpty: Boolean =
    value.nonEmpty
  def map(f: String => String): Type =
    Type(f(value))
}

object Type {
  implicit val ordering: Ordering[Type] =
    Ordering[String].on(_.value)

  val jar = Type("jar")
  val testJar = Type("test-jar")
  val bundle = Type("bundle")
  val doc = Type("doc")
  val source = Type("src")

  // Typo for doc and src ???
  val javadoc = Type("javadoc")
  val javaSource = Type("java-source")

  val ivy = Type("ivy")
  val pom = Type("pom")

  val empty = Type("")
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

  val tests = Classifier("tests")
  val javadoc = Classifier("javadoc")
  val sources = Classifier("sources")
}

// Maven-specific
final case class Attributes(
  `type`: Type,
  classifier: Classifier
) {
  def packaging: String =
    if (`type`.isEmpty)
      "jar"
    else
      `type`.value

  def packagingAndClassifier: String =
    if (isEmpty)
      ""
    else if (classifier.isEmpty)
      packaging
    else
      s"$packaging:$classifier"

  def publication(name: String, ext: String): Publication =
    Publication(name, `type`, ext, classifier)

  def isEmpty: Boolean =
    `type`.isEmpty && classifier.isEmpty
}

object Attributes {
  val empty = Attributes(Type.empty, Classifier.empty)
}

final case class Project(
  module: Module,
  version: String,
  // First String is configuration (scope for Maven)
  dependencies: Seq[(String, Dependency)],
  // For Maven, this is the standard scopes as an Ivy configuration
  configurations: Map[String, Seq[String]],

  // Maven-specific
  parent: Option[(Module, String)],
  dependencyManagement: Seq[(String, Dependency)],
  properties: Seq[(String, String)],
  profiles: Seq[Profile],
  versions: Option[Versions],
  snapshotVersioning: Option[SnapshotVersioning],
  packagingOpt: Option[Type],
  relocated: Boolean,

  /**
    * Optional exact version used to get this project metadata.
    * May not match `version` for projects having a wrong version in their metadata.
    */
  actualVersionOpt: Option[String],

  // First String is configuration
  publications: Seq[(String, Publication)],

  // Extra infos, not used during resolution
  info: Info
) {
  lazy val moduleVersion = (module, version)

  /** All configurations that each configuration extends, including the ones it extends transitively */
  lazy val allConfigurations: Map[String, Set[String]] =
    Orders.allConfigurations(configurations)

  /**
    * Version used to get this project metadata if available, else the version from metadata.
    * May not match `version` for projects having a wrong version in their metadata, if the actual version was kept
    * around.
    */
  def actualVersion: String = actualVersionOpt.getOrElse(version)
}

/** Extra project info, not used during resolution */
final case class Info(
  description: String,
  homePage: String,
  licenses: Seq[(String, Option[String])],
  developers: Seq[Info.Developer],
  publication: Option[Versions.DateTime]
)

object Info {
  final case class Developer(
    id: String,
    name: String,
    url: String
  )

  val empty = Info("", "", Nil, Nil, None)
}

// Maven-specific
final case class Profile(
  id: String,
  activeByDefault: Option[Boolean],
  activation: Activation,
  dependencies: Seq[(String, Dependency)],
  dependencyManagement: Seq[(String, Dependency)],
  properties: Map[String, String]
)

// Maven-specific
final case class Versions(
  latest: String,
  release: String,
  available: List[String],
  lastUpdated: Option[Versions.DateTime]
)

object Versions {
  final case class DateTime(
    year: Int,
    month: Int,
    day: Int,
    hour: Int,
    minute: Int,
    second: Int
  )
}

// Maven-specific
final case class SnapshotVersion(
  classifier: Classifier,
  extension: String,
  value: String,
  updated: Option[Versions.DateTime]
)

// Maven-specific
final case class SnapshotVersioning(
  module: Module,
  version: String,
  latest: String,
  release: String,
  timestamp: String,
  buildNumber: Option[Int],
  localCopy: Option[Boolean],
  lastUpdated: Option[Versions.DateTime],
  snapshotVersions: Seq[SnapshotVersion]
)

// Ivy-specific
final case class Publication(
  name: String,
  `type`: Type,
  ext: String,
  classifier: Classifier
) {
  def attributes: Attributes = Attributes(`type`, classifier)
}

final case class Artifact(
  url: String,
  checksumUrls: Map[String, String],
  extra: Map[String, Artifact],
  changing: Boolean,
  optional: Boolean,
  authentication: Option[Authentication]
) {

  // attributes, `type`, and classifier don't live here anymore.
  // Get them via the dependencyArtifacts method on Resolution.

  @deprecated("Use optional instead", "1.1.0-M8")
  def isOptional: Boolean = optional
}

object Artifact {

  trait Source {
    def artifacts(
      dependency: Dependency,
      project: Project,
      overrideClassifiers: Option[Seq[Classifier]]
    ): Seq[(Attributes, Artifact)]
  }

}

final case class Authentication(
  user: String,
  password: String
) {
  override def toString: String =
    s"Authentication($user, *******)"
}
