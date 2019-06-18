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

  def repr: String =
    s"${organization.value}:$nameWithAttributes"

  override def toString: String =
    repr

  def orgName: String =
    s"${organization.value}:${name.value}"

  override final lazy val hashCode = Module.unapply(this).get.hashCode()
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
  val all = Type("*")

  object Exotic {
    // https://github.com/sbt/sbt/blob/47cd001eea8ef42b7c1db9ffdf48bec16b8f733b/main/src/main/scala/sbt/Defaults.scala#L227
    val mavenPlugin = Type("maven-plugin")

    // https://github.com/sbt/librarymanagement/blob/bb2c73e183fa52e2fb4b9ae7aca55799f3ff6624/ivy/src/main/scala/sbt/internal/librarymanagement/CustomPomParser.scala#L79
    val eclipsePlugin = Type("eclipse-plugin")
    val hk2 = Type("hk2-jar")
    val orbit = Type("orbit")
    val scalaJar = Type("scala-jar")
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

  val tests = Classifier("tests")
  val javadoc = Classifier("javadoc")
  val sources = Classifier("sources")
}

final case class Extension(value: String) extends AnyVal {
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
  val test = Configuration("test")

  val default = Configuration("default")
  val defaultCompile = Configuration("default(compile)")

  val provided = Configuration("provided")
  val `import` = Configuration("import")
  val optional = Configuration("optional")

  val all = Configuration("*")

  def join(confs: Configuration*): Configuration =
    Configuration(confs.map(_.value).mkString(";"))
}

final case class Attributes(
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
}

object Attributes {
  val empty = Attributes(Type.empty, Classifier.empty)
}

final case class Project(
  module: Module,
  version: String,
  // First String is configuration (scope for Maven)
  dependencies: Seq[(Configuration, Dependency)],
  // For Maven, this is the standard scopes as an Ivy configuration
  configurations: Map[Configuration, Seq[Configuration]],

  // Maven-specific
  parent: Option[(Module, String)],
  dependencyManagement: Seq[(Configuration, Dependency)],
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

  publications: Seq[(Configuration, Publication)],

  // Extra infos, not used during resolution
  info: Info
) {
  lazy val moduleVersion = (module, version)

  /** All configurations that each configuration extends, including the ones it extends transitively */
  lazy val allConfigurations: Map[Configuration, Set[Configuration]] =
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
  dependencies: Seq[(Configuration, Dependency)],
  dependencyManagement: Seq[(Configuration, Dependency)],
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
  extension: Extension,
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

final case class Publication(
  name: String,
  `type`: Type,
  ext: Extension,
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
}

object Artifact {

  trait Source {
    def artifacts(
      dependency: Dependency,
      project: Project,
      overrideClassifiers: Option[Seq[Classifier]]
    ): Seq[(Publication, Artifact)]
  }

}

final case class Authentication(
  user: String,
  password: String = "",
  optional: Boolean = false,
  realmOpt: Option[String] = None,
  httpsOnly: Boolean = true,
  passOnRedirect: Boolean = false
) {
  override def toString: String =
    s"Authentication($user, *******, $optional, $realmOpt, $httpsOnly, $passOnRedirect)"
  def userOnly: Boolean =
    this == Authentication(user)
}
