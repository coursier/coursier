package coursier.core

import java.util.concurrent.ConcurrentMap

import coursier.util.Artifact
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
}

object Attributes {
  val empty = Attributes(Type.empty, Classifier.empty)
}

@data class Project(
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
  /** Optional exact version used to get this project metadata. May not match `version` for projects
    * having a wrong version in their metadata.
    */
  actualVersionOpt: Option[String],
  publications: Seq[(Configuration, Publication)],

  // Extra infos, not used during resolution
  info: Info
) {
  lazy val moduleVersion = (module, version)

  /** All configurations that each configuration extends, including the ones it extends transitively
    */
  lazy val allConfigurations: Map[Configuration, Set[Configuration]] =
    Orders.allConfigurations0(configurations)

  /** Version used to get this project metadata if available, else the version from metadata. May
    * not match `version` for projects having a wrong version in their metadata, if the actual
    * version was kept around.
    */
  def actualVersion: String = actualVersionOpt.getOrElse(version)

  final override lazy val hashCode = tuple.hashCode
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
  value: String,
  updated: Option[Versions.DateTime]
)

// Maven-specific
@data class SnapshotVersioning(
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

@data(apply = false, settersCallApply = true) class Publication(
  name: String,
  `type`: Type,
  ext: Extension,
  classifier: Classifier
) {
  def attributes: Attributes = Attributes(`type`, classifier)
  def isEmpty: Boolean =
    name.isEmpty && `type`.isEmpty && ext.isEmpty && classifier.isEmpty

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

trait ArtifactSource {
  def artifacts(
    dependency: Dependency,
    project: Project,
    overrideClassifiers: Option[Seq[Classifier]]
  ): Seq[(Publication, Artifact)]
}
