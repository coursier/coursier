package coursier.maven

import coursier.core.{
  Activation,
  Attributes,
  Classifier,
  Configuration,
  Dependency,
  Extension,
  Info,
  Module,
  ModuleName,
  Organization,
  Overrides,
  Profile,
  Project,
  SnapshotVersion,
  SnapshotVersioning,
  Type,
  Variant,
  VariantSelector,
  Versions
}
import coursier.core.Validation._
import coursier.util.Traverse.TraverseOps
import coursier.version.{Version, VersionConstraint, VersionParse}

import scala.collection.compat._

object Pom {
  import coursier.util.Xml._

  /** Returns either a property's key-value pair or an error if the elem is not an element.
    *
    * This method trims all spaces, whereas Maven has an option to preserve them.
    *
    * @param elem
    *   a property element
    * @return
    *   the key and the value of the property
    * @see
    *   [[https://issues.apache.org/jira/browse/MNG-5380]]
    */
  def property(elem: Node): Either[String, (String, String)] =
    // Not matching with Text, which fails on scala-js if the property value has xml comments
    if (elem.isElement) Right(elem.label -> elem.textContent.trim)
    else Left(s"Can't parse property $elem")

  // TODO Allow no version in some contexts
  private def module(
    node: Node,
    defaultGroupId: Option[Organization] = None,
    defaultArtifactId: Option[ModuleName] = None
  ): Either[String, Module] =
    for {
      organization <- {
        val e = text(node, "groupId", "Organization")
          .flatMap(validateCoordinate(_, "groupId"))
          .map(Organization(_))
        defaultGroupId.fold(e)(g => Right(e.getOrElse(g)))
      }
      name <- {
        val n = text(node, "artifactId", "Name")
          .flatMap(validateCoordinate(_, "artifactId"))
          .map(ModuleName(_))
        defaultArtifactId.fold(n)(n0 => Right(n.getOrElse(n0)))
      }
    } yield Module(organization, name, Map.empty).trim

  private def readVersion(node: Node): Version =
    Version(text(node, "version", "Version").getOrElse("").trim)

  private def readVersionConstraint(node: Node): VersionConstraint =
    VersionConstraint(text(node, "version", "Version").getOrElse("").trim)

  def dependency(node: Node): Either[String, (Configuration, Dependency)] =
    module(node).flatMap { mod =>

      val version0 = readVersionConstraint(node)
      val scopeOpt = text(node, "scope", "")
        .map(Configuration(_))
        .toOption
      val typeOpt = text(node, "type", "")
        .map(Type(_))
        .toOption
      val classifierOpt = text(node, "classifier", "")
        .map(Classifier(_))
        .toOption
      val xmlExclusions = node.children
        .find(_.label == "exclusions")
        .map(_.children.filter(_.label == "exclusion"))
        .getOrElse(Seq.empty)

      for {
        exclusions <- xmlExclusions
          .eitherTraverse(module(_, defaultArtifactId = Some(ModuleName("*"))))
        version <- validateCoordinate(version0.asString, "version")
      } yield {
        val optional = text(node, "optional", "").toSeq.contains("true")

        scopeOpt.getOrElse(Configuration.empty) -> Dependency(
          mod,
          version0,
          VariantSelector.emptyConfiguration,
          exclusions.map(mod => (mod.organization, mod.name)).toSet,
          Attributes(typeOpt.getOrElse(Type.empty), classifierOpt.getOrElse(Classifier.empty)),
          optional,
          transitive = true
        )
      }
    }

  private def profileActivation(node: Node): (Option[Boolean], Activation) = {
    val byDefault =
      text(node, "activeByDefault", "").toOption.flatMap {
        case "true"  => Some(true)
        case "false" => Some(false)
        case _       => None
      }

    val properties = node.children
      .filter(_.label == "property")
      .flatMap { p =>
        for {
          name <- text(p, "name", "").toOption
          valueOpt = text(p, "value", "").toOption
        } yield (name, valueOpt)
      }

    val osNodeOpt = node.children.collectFirst { case n if n.label == "os" => n }

    val os = Activation.Os(
      osNodeOpt.flatMap(n => text(n, "arch", "").toOption),
      osNodeOpt.flatMap(n => text(n, "family", "").toOption).toSet,
      osNodeOpt.flatMap(n => text(n, "name", "").toOption),
      osNodeOpt.flatMap(n => text(n, "version", "").toOption)
    )

    val jdk = text(node, "jdk", "").toOption.flatMap { s =>
      VersionParse.versionInterval(s)
        .orElse(VersionParse.multiVersionInterval(s))
        .map(Left(_))
        .orElse(VersionParse.version(s).map(v => Right(Seq(v))))
    }

    val activation = Activation(properties, os, jdk)

    (byDefault, activation)
  }

  def profile(node: Node): Either[String, Profile] = {

    val id = text(node, "id", "Profile ID").getOrElse("")

    val xmlActivationOpt = node.children
      .find(_.label == "activation")
    val (activeByDefault, activation) =
      xmlActivationOpt.fold((Option.empty[Boolean], Activation.empty))(profileActivation)

    val xmlDeps = node.children
      .find(_.label == "dependencies")
      .map(_.children.filter(_.label == "dependency"))
      .getOrElse(Seq.empty)

    for {
      deps <- xmlDeps.eitherTraverse(dependency)

      depMgmts <- node
        .children
        .find(_.label == "dependencyManagement")
        .flatMap(_.children.find(_.label == "dependencies"))
        .map(_.children.filter(_.label == "dependency"))
        .getOrElse(Seq.empty)
        .eitherTraverse(dependency)

      properties <- node
        .children
        .find(_.label == "properties")
        .map(_.children.collect { case elem if elem.isElement => elem })
        .getOrElse(Seq.empty)
        .eitherTraverse(property)

    } yield Profile(id, activeByDefault, activation, deps, depMgmts, properties.toMap)
  }

  def packagingOpt(pom: Node): Option[Type] =
    text(pom, "packaging", "").map(Type(_)).toOption

  def project(pom: Node): Either[String, Project] =
    for {
      projModule <- module(pom, defaultGroupId = Some(Organization("")))

      parentOpt = pom.children.find(_.label == "parent")
      parentModuleOpt <- parentOpt
        .map(module(_).map(Some(_)))
        .getOrElse(Right(None))
      parentVersionOpt = parentOpt.map(readVersion)

      xmlDeps = pom.children
        .find(_.label == "dependencies")
        .map(_.children.filter(_.label == "dependency"))
        .getOrElse(Seq.empty)
      deps <- xmlDeps.eitherTraverse(dependency)

      xmlDepMgmts = pom.children
        .find(_.label == "dependencyManagement")
        .flatMap(_.children.find(_.label == "dependencies"))
        .map(_.children.filter(_.label == "dependency"))
        .getOrElse(Seq.empty)
      depMgmts <- xmlDepMgmts.eitherTraverse(dependency)

      groupId <- Some(projModule.organization).filter(_.value.nonEmpty)
        .orElse(parentModuleOpt.map(_.organization).filter(_.value.nonEmpty))
        .toRight("No organization found")
      version <- Some(readVersion(pom)).filter(_.asString.nonEmpty)
        .orElse(parentVersionOpt.filter(_.asString.nonEmpty))
        .toRight("No version found")

      _ <- parentVersionOpt
        .map(v => if (v.asString.isEmpty) Left("Parent version missing") else Right(()))
        .getOrElse(Right(()))
      _ <- parentModuleOpt
        .map { mod =>
          if (mod.organization.value.isEmpty) Left("Parent organization missing")
          else Right(())
        }
        .getOrElse(Right(()))

      xmlProperties = pom.children
        .find(_.label == "properties")
        .map(_.children.collect { case elem if elem.isElement => elem })
        .getOrElse(Seq.empty)
      properties <- xmlProperties.eitherTraverse(property)

      xmlProfiles = pom
        .children
        .find(_.label == "profiles")
        .map(_.children.filter(_.label == "profile"))
        .getOrElse(Seq.empty)

      profiles <- xmlProfiles.eitherTraverse(profile)

    } yield {

      val description = pom.children
        .find(_.label == "description")
        .map(_.textContent)
        .getOrElse("")

      val homePage = pom.children
        .find(_.label == "url")
        .map(_.textContent)
        .getOrElse("")

      val licenses = pom.children
        .find(_.label == "licenses")
        .toSeq
        .flatMap(_.children)
        .filter(_.label == "license")
        .flatMap { n =>
          text(n, "name", "License name").toOption.map { name =>
            (name, text(n, "url", "License URL").toOption)
          }.toSeq
        }

      val developers = pom.children
        .find(_.label == "developers")
        .toSeq
        .flatMap(_.children)
        .filter(_.label == "developer")
        .map { n =>
          for {
            id   <- text(n, "id", "Developer ID")
            name <- text(n, "name", "Developer name")
            url  <- text(n, "url", "Developer URL")
          } yield Info.Developer(id, name, url)
        }
        .collect {
          case Right(d) => d
        }

      val scm = pom.children
        .find(_.label == "scm")
        .flatMap { n =>
          Option(Info.Scm(
            text(n, "url", "A publicly browsable repository").toOption,
            text(n, "connection", "Requires read access").toOption,
            text(n, "developerConnection", "Requires write access").toOption
          )).filter(scm =>
            scm.url.isDefined || scm.connection.isDefined || scm.developerConnection.isDefined
          )
        }

      val finalProjModule = projModule.withOrganization(groupId)

      val relocationDependencyOpt = pom
        .children
        .find(_.label == "distributionManagement")
        .flatMap(_.children.find(_.label == "relocation"))
        .map { n =>
          // see https://maven.apache.org/guides/mini/guide-relocation.html

          val relocatedGroupId = text(n, "groupId", "")
            .map(Organization(_))
            .getOrElse(finalProjModule.organization)
          val relocatedArtifactId = text(n, "artifactId", "")
            .map(ModuleName(_))
            .getOrElse(finalProjModule.name)
          val relocatedVersion = text(n, "version", "").map(Version(_)).getOrElse(version)

          Variant.emptyConfiguration -> Dependency(
            finalProjModule
              .withOrganization(relocatedGroupId)
              .withName(relocatedArtifactId),
            VersionConstraint.fromVersion(relocatedVersion),
            VariantSelector.emptyConfiguration,
            Set.empty[(Organization, ModuleName)],
            Attributes.empty,
            optional = false,
            transitive = true
          )
        }

      Project(
        finalProjModule,
        version,
        relocationDependencyOpt.toSeq ++ deps.map {
          case (conf, dep) =>
            (Variant.Configuration(conf), dep)
        },
        // this is customized later on in MavenRepositoryInternal
        Map.empty[Configuration, Seq[Configuration]],
        parentModuleOpt.map((_, parentVersionOpt.getOrElse(Version.zero))),
        depMgmts.map {
          case (conf, dep) =>
            (Variant.Configuration(conf), dep)
        },
        properties,
        profiles,
        None,
        None,
        packagingOpt(pom),
        relocationDependencyOpt.nonEmpty,
        None,
        Nil,
        Info(
          description,
          homePage,
          licenses,
          developers,
          None,
          scm
        ),
        Overrides.empty,
        Map.empty,
        Map.empty
      )
    }

  def versions(node: Node): Either[String, Versions] =
    for {
      organization <- text(node, "groupId", "Organization") // Ignored
      name         <- text(node, "artifactId", "Name")      // Ignored

      xmlVersioning <- node.children
        .find(_.label == "versioning")
        .toRight("Versioning info not found in metadata")

    } yield {

      val latest = text(xmlVersioning, "latest", "Latest version")
        .map(Version(_))
        .getOrElse(Version.zero)
      val release = text(xmlVersioning, "release", "Release version")
        .map(Version(_))
        .getOrElse(Version.zero)

      val versionsOpt = xmlVersioning.children
        .find(_.label == "versions")
        .map { node =>
          node.children
            .filter(_.label == "version")
            .flatMap(_.children.collectFirst {
              case Text(t) => Version(t)
            })
        }

      val lastUpdatedOpt = text(xmlVersioning, "lastUpdated", "Last update date and time")
        .toOption
        .flatMap(parseDateTime)

      Versions(latest, release, versionsOpt.map(_.toList).getOrElse(Nil), lastUpdatedOpt)
    }

  def snapshotVersion(node: Node): Either[String, SnapshotVersion] = {

    def textOrEmpty(name: String, desc: String): String =
      text(node, name, desc).getOrElse("")

    val classifier = Classifier(textOrEmpty("classifier", "Classifier"))
    val ext        = Extension(textOrEmpty("extension", "Extensions"))
    val value = text(node, "value", "Value")
      .map(Version(_))
      .getOrElse(Version.zero)

    val updatedOpt = text(node, "updated", "Updated")
      .toOption
      .flatMap(parseDateTime)

    Right(SnapshotVersion(
      classifier,
      ext,
      value,
      updatedOpt
    ))
  }

  /** If `snapshotVersion` is missing, guess it based on `version`, `timestamp` and `buildNumber`,
    * as is done in:
    * https://github.com/sbt/ivy/blob/2.3.x-sbt/src/java/org/apache/ivy/plugins/resolver/IBiblioResolver.java
    */
  def guessedSnapshotVersion(
    version: String,
    timestamp: String,
    buildNumber: Int
  ): SnapshotVersion = {
    val value = Version(s"${version.dropRight("SNAPSHOT".length)}$timestamp-$buildNumber")
    SnapshotVersion(Classifier("*"), Extension("*"), value, None)
  }

  def snapshotVersioning(node: Node): Either[String, SnapshotVersioning] =
    // FIXME Quite similar to Versions above
    for {
      organization <- text(node, "groupId", "Organization").map(Organization(_))
      name         <- text(node, "artifactId", "Name").map(ModuleName(_))

      xmlVersioning <- node
        .children
        .find(_.label == "versioning")
        .toRight("Versioning info not found in metadata")

      snapshotVersions <- {

        val xmlSnapshotVersions = xmlVersioning
          .children
          .find(_.label == "snapshotVersions")
          .map(_.children.filter(_.label == "snapshotVersion"))
          .getOrElse(Seq.empty)

        xmlSnapshotVersions.eitherTraverse(snapshotVersion)
      }
    } yield {

      val version = readVersion(node)

      val latest = text(xmlVersioning, "latest", "Latest version")
        .map(Version(_))
        .getOrElse(Version.zero)
      val release = text(xmlVersioning, "release", "Release version")
        .map(Version(_))
        .getOrElse(Version.zero)

      val lastUpdatedOpt = text(xmlVersioning, "lastUpdated", "Last update date and time")
        .toOption
        .flatMap(parseDateTime)

      val xmlSnapshotOpt = xmlVersioning
        .children
        .find(_.label == "snapshot")

      val timestamp = xmlSnapshotOpt
        .flatMap(text(_, "timestamp", "Snapshot timestamp").toOption)
        .getOrElse("")

      val buildNumber = xmlSnapshotOpt
        .flatMap(text(_, "buildNumber", "Snapshot build number").toOption)
        .filter(s => s.nonEmpty && s.forall(_.isDigit))
        .map(_.toInt)

      val localCopy = xmlSnapshotOpt
        .flatMap(text(_, "localCopy", "Snapshot local copy").toOption)
        .collect {
          case "true"  => true
          case "false" => false
        }

      SnapshotVersioning(
        Module(organization, name, Map.empty),
        version,
        latest,
        release,
        timestamp,
        buildNumber,
        localCopy,
        lastUpdatedOpt,
        if (!snapshotVersions.isEmpty)
          snapshotVersions
        else
          buildNumber.map(bn => guessedSnapshotVersion(version.asString, timestamp, bn)).toList
      )
    }

  val extraAttributeSeparator = ":#@#:"
  val extraAttributePrefix    = "+"

  val extraAttributeOrg     = "organisation"
  val extraAttributeName    = "module"
  val extraAttributeVersion = "revision"

  val extraAttributeBase = Set(
    extraAttributeOrg,
    extraAttributeName,
    extraAttributeVersion,
    "branch"
  )

  val extraAttributeDropPrefix = "e:"

  def addOptionalDependenciesInConfig(
    proj: Project,
    fromConfigs: Set[Configuration],
    optionalConfig: Configuration
  ): Project = {

    val optionalDeps = proj.dependencies0.collect {
      case (c: Variant.Configuration, dep) if dep.optional && fromConfigs(c.configuration) =>
        Variant.Configuration(optionalConfig) -> dep.withOptional(false)
    }

    val optConfigThing = proj.configurations.getOrElse(optionalConfig, Nil) ++
      fromConfigs.filter(_.nonEmpty)
    val configurations = proj.configurations + (optionalConfig -> optConfigThing.distinct)

    proj
      .withConfigurations(configurations)
      .withDependencies0(proj.dependencies0 ++ optionalDeps)
  }
}
