package coursier.maven

import coursier.core._
import coursier.util.Traverse.TraverseOps

object Pom {
  import coursier.util.Xml._

  /**
    * Returns either a property's key-value pair or an error if the elem is not an element.
    *
    * This method trims all spaces, whereas Maven has an option to preserve them.
    *
    * @param elem a property element
    * @return the key and the value of the property
    * @see [[https://issues.apache.org/jira/browse/MNG-5380]]
    */
  def property(elem: Node): Either[String, (String, String)] = {
    // Not matching with Text, which fails on scala-js if the property value has xml comments
    if (elem.isElement) Right(elem.label -> elem.textContent.trim)
    else Left(s"Can't parse property $elem")
  }

  // TODO Allow no version in some contexts
  private def module(
    node: Node,
    defaultGroupId: Option[Organization] = None,
    defaultArtifactId: Option[ModuleName] = None
  ): Either[String, Module] = {
    for {
      organization <- {
        val e = text(node, "groupId", "Organization").map(Organization(_))
        defaultGroupId.fold(e)(g => Right(e.getOrElse(g)))
      }
      name <- {
        val n = text(node, "artifactId", "Name").map(ModuleName(_))
        defaultArtifactId.fold(n)(n0 => Right(n.getOrElse(n0)))
      }
    } yield Module(organization, name, Map.empty).trim
  }

  private def readVersion(node: Node) =
    text(node, "version", "Version").getOrElse("").trim

  def dependency(node: Node): Either[String, (Configuration, Dependency)] =
    module(node).flatMap { mod =>

      val version0 = readVersion(node)
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

      xmlExclusions
        .eitherTraverse(module(_, defaultArtifactId = Some(ModuleName("*"))))
        .map { exclusions =>

          val optional = text(node, "optional", "").toSeq.contains("true")

          scopeOpt.getOrElse(Configuration.empty) -> Dependency(
            mod,
            version0,
            Configuration.empty,
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
        case "true" => Some(true)
        case "false" => Some(false)
        case _ => None
      }

    val properties = node.children
      .filter(_.label == "property")
      .flatMap { p =>
        for{
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
      Parse.versionInterval(s)
        .orElse(Parse.multiVersionInterval(s))
        .map(Left(_))
        .orElse(Parse.version(s).map(v => Right(Seq(v))))
    }

    val activation = Activation(properties, os, jdk)

    (byDefault, activation)
  }

  def profile(node: Node): Either[String, Profile] = {

    val id = text(node, "id", "Profile ID").getOrElse("")

    val xmlActivationOpt = node.children
      .find(_.label == "activation")
    val (activeByDefault, activation) = xmlActivationOpt.fold((Option.empty[Boolean], Activation.empty))(profileActivation)

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
      version <- Some(readVersion(pom)).filter(_.nonEmpty)
        .orElse(parentVersionOpt.filter(_.nonEmpty))
        .toRight("No version found")

      _ <- parentVersionOpt
        .map(v => if (v.isEmpty) Left("Parent version missing") else Right(()))
        .getOrElse(Right(()))
      _ <- parentModuleOpt
        .map(mod => if (mod.organization.value.isEmpty) Left("Parent organization missing") else Right(()))
        .getOrElse(Right(()))

      xmlProperties = pom.children
        .find(_.label == "properties")
        .map(_.children.collect{case elem if elem.isElement => elem})
        .getOrElse(Seq.empty)
      properties <- xmlProperties.eitherTraverse(property)

      xmlProfiles = pom
        .children
        .find(_.label == "profiles")
        .map(_.children.filter(_.label == "profile"))
        .getOrElse(Seq.empty)

      profiles <- xmlProfiles.eitherTraverse(profile)

      extraAttrs <- properties
        .collectFirst { case ("extraDependencyAttributes", s) => extraAttributes(s) }
        .getOrElse(Right(Map.empty))

    } yield {

      val extraAttrsMap = extraAttrs
        .map {
          case (mod, ver) =>
            (mod.withAttributes(Map.empty), ver) -> mod.attributes
        }
        .toMap

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
            id <- text(n, "id", "Developer ID")
            name <- text(n, "name", "Developer name")
            url <- text(n, "url", "Developer URL")
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
          )).filter(scm => scm.url.isDefined || scm.connection.isDefined || scm.developerConnection.isDefined)
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
          val relocatedVersion = text(n, "version", "").getOrElse(version)

          Configuration.empty -> Dependency(
            finalProjModule
              .withOrganization(relocatedGroupId)
              .withName(relocatedArtifactId),
            relocatedVersion,
            Configuration.empty,
            Set.empty[(Organization, ModuleName)],
            Attributes.empty,
            optional = false,
            transitive = true
          )
        }

      Project(
        finalProjModule,
        version,
        (relocationDependencyOpt.toSeq ++ deps).map {
          case (config, dep0) =>
            val dep = extraAttrsMap.get(dep0.moduleVersion).fold(dep0)(attrs =>
              dep0.withModule(dep0.module.withAttributes(attrs))
            )
            config -> dep
        },
        Map.empty,
        parentModuleOpt.map((_, parentVersionOpt.getOrElse(""))),
        depMgmts,
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
        )
      )
    }

  def versions(node: Node): Either[String, Versions] = {

    for {
      organization <- text(node, "groupId", "Organization") // Ignored
      name <- text(node, "artifactId", "Name") // Ignored

      xmlVersioning <- node.children
        .find(_.label == "versioning")
        .toRight("Versioning info not found in metadata")

    } yield {

      val latest = text(xmlVersioning, "latest", "Latest version").getOrElse("")
      val release = text(xmlVersioning, "release", "Release version").getOrElse("")

      val versionsOpt = xmlVersioning.children
        .find(_.label == "versions")
        .map(_.children.filter(_.label == "version").flatMap(_.children.collectFirst { case Text(t) => t }))

      val lastUpdatedOpt = text(xmlVersioning, "lastUpdated", "Last update date and time")
        .toOption
        .flatMap(parseDateTime)

      Versions(latest, release, versionsOpt.map(_.toList).getOrElse(Nil), lastUpdatedOpt)
    }
  }

  def snapshotVersion(node: Node): Either[String, SnapshotVersion] = {

    def textOrEmpty(name: String, desc: String): String =
      text(node, name, desc).getOrElse("")

    val classifier = Classifier(textOrEmpty("classifier", "Classifier"))
    val ext = Extension(textOrEmpty("extension", "Extensions"))
    val value = textOrEmpty("value", "Value")

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

  /** If `snapshotVersion` is missing, guess it based on
    * `version`, `timestamp` and `buildNumber`, as is done in:
    * https://github.com/sbt/ivy/blob/2.3.x-sbt/src/java/org/apache/ivy/plugins/resolver/IBiblioResolver.java
    */
  def guessedSnapshotVersion(
    version: String,
    timestamp: String,
    buildNumber: Int
  ): SnapshotVersion = {
    val value = s"${version.dropRight("SNAPSHOT".length)}$timestamp-$buildNumber"
    SnapshotVersion(Classifier("*"), Extension("*"), value, None)
  }

  def snapshotVersioning(node: Node): Either[String, SnapshotVersioning] =
    // FIXME Quite similar to Versions above
    for {
      organization <- text(node, "groupId", "Organization").map(Organization(_))
      name <- text(node, "artifactId", "Name").map(ModuleName(_))

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

      val latest = text(xmlVersioning, "latest", "Latest version").getOrElse("")
      val release = text(xmlVersioning, "release", "Release version").getOrElse("")

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
          case "true" => true
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
          buildNumber.map(bn => guessedSnapshotVersion(version, timestamp, bn)).toList
      )
    }

  val extraAttributeSeparator = ":#@#:"
  val extraAttributePrefix = "+"

  val extraAttributeOrg = "organisation"
  val extraAttributeName = "module"
  val extraAttributeVersion = "revision"

  val extraAttributeBase = Set(
    extraAttributeOrg,
    extraAttributeName,
    extraAttributeVersion,
    "branch"
  )

  val extraAttributeDropPrefix = "e:"

  def extraAttribute(s: String): Either[String, (Module, String)] = {
    // vaguely does the same as:
    // https://github.com/apache/ant-ivy/blob/2.2.0/src/java/org/apache/ivy/core/module/id/ModuleRevisionId.java#L291

    // dropping the attributes with a value of NULL here...

    val rawParts = s.split(extraAttributeSeparator).toSeq

    val partsOrError =
      if (rawParts.length % 2 == 0) {
        val malformed = rawParts.filter(!_.startsWith(extraAttributePrefix))
        if (malformed.isEmpty)
          Right(rawParts.map(_.drop(extraAttributePrefix.length)))
        else
          Left(s"Malformed attributes ${malformed.map("'"+_+"'").mkString(", ")} in extra attributes '$s'")
      } else
        Left(s"Malformed extra attributes '$s'")

    def attrFrom(attrs: Map[String, String], name: String): Either[String, String] =
      attrs
        .get(name)
        .toRight(s"$name not found in extra attributes '$s'")

    for {
      parts <- partsOrError
      attrs = parts
        .grouped(2)
        .collect {
          case Seq(k, v) if v != "NULL" =>
            k.stripPrefix(extraAttributeDropPrefix) -> v
        }
        .toMap
      org <- attrFrom(attrs, extraAttributeOrg).map(Organization(_))
      name <- attrFrom(attrs, extraAttributeName).map(ModuleName(_))
      version <- attrFrom(attrs, extraAttributeVersion)
    } yield {
      val remainingAttrs = attrs.filterKeys(!extraAttributeBase(_))
      (Module(org, name, remainingAttrs.toVector.toMap), version)
    }
  }

  def extraAttributes(s: String): Either[String, Seq[(Module, String)]] = {

    val lines = s.split('\n').toSeq.map(_.trim).filter(_.nonEmpty)

    lines.foldLeft[Either[String, Seq[(Module, String)]]](Right(Vector.empty)) {
      case (acc, line) =>
        for {
          modVers <- acc
          modVer <- extraAttribute(line)
        } yield modVers :+ modVer
    }
  }

  def addOptionalDependenciesInConfig(
    proj: Project,
    fromConfigs: Set[Configuration],
    optionalConfig: Configuration
  ): Project = {

    val optionalDeps = proj.dependencies.collect {
      case (conf, dep) if dep.optional && fromConfigs(conf) =>
        optionalConfig -> dep.withOptional(false)
    }

    val configurations = proj.configurations +
      (optionalConfig -> (proj.configurations.getOrElse(optionalConfig, Nil) ++ fromConfigs.filter(_.nonEmpty)).distinct)

    proj
      .withConfigurations(configurations)
      .withDependencies(proj.dependencies ++ optionalDeps)
  }
}
