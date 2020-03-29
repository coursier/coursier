package coursier.publish

import coursier.core.{Configuration, ModuleName, Organization, Type}

import scala.collection.mutable
import scala.xml.{Elem, Node, NodeSeq}

object Pom {

  // TODO Check https://github.com/lihaoyi/mill/pull/144/files
  final case class License(name: String, url: String)

  object License {
    def apache2: License =
      License("Apache-2.0", "https://spdx.org/licenses/Apache-2.0.html")

    lazy val all = Seq(
      apache2
    )

    lazy val map = all.map(l => l.name -> l).toMap
  }


  final case class Scm(
    url: String,
    connection: String,
    developerConnection: String
  )

  object Scm {
    def gitHub(org: String, project: String): Scm =
      Scm(
        s"https://github.com/$org/$project.git",
        s"scm:git:github.com/$org/$project.git",
        s"scm:git:git@github.com:$org/$project.git"
      )
  }


  // FIXME What's mandatory? What's not?
  final case class Developer(
    id: String,
    name: String,
    url: String,
    mail: Option[String]
  )


  def create(
    organization: Organization,
    moduleName: ModuleName,
    version: String,
    packaging: Option[Type] = None,
    description: Option[String] = None,
    url: Option[String] = None,
    name: Option[String] = None,
    // TODO Accept full-fledged coursier.Dependency
    dependencies: Seq[(Organization, ModuleName, String, Option[Configuration])] = Nil,
    license: Option[License] = None,
    scm: Option[Scm] = None,
    developers: Seq[Developer] = Nil
  ): String = {

    val nodes = new mutable.ListBuffer[NodeSeq]

    nodes ++= Seq(
      <modelVersion>4.0.0</modelVersion>,
      <groupId>{organization.value}</groupId>,
      <artifactId>{moduleName.value}</artifactId>,
      <version>{version}</version>
    )

    for (p <- packaging)
      nodes += <packaging>{p.value}</packaging>

    for (u <- url)
      nodes += <url>{u}</url>

    for (d <- description)
      nodes += <description>{d}</description>

    for (n <- name)
      nodes += <name>{n}</name>

    nodes += {
      val urlNodeOpt = url.fold[NodeSeq](Nil)(u => <url>{u}</url>)
      <organization>
        <name>{organization.value}</name>
        {urlNodeOpt}
      </organization>
    }

    for (l <- license)
      nodes +=
        <licenses>
          <license>
            <name>{l.name}</name>
            <url>{l.url}</url>
            <distribution>repo</distribution>
          </license>
        </licenses>

    for (s <- scm)
      nodes +=
        <scm>
          <url>{s.url}</url>
          <connection>{s.connection}</connection>
          <developerConnection>{s.developerConnection}</developerConnection>
        </scm>

    if (developers.nonEmpty)
      nodes +=
        <developers>
          {
            developers.map { d =>
              <developer>
                <id>{d.id}</id>
                <name>{d.name}</name>
                <url>{d.url}</url>
              </developer>
              // + optional mail
            }
          }
        </developers>

    if (dependencies.nonEmpty)
      nodes +=
        <dependencies>
          {
            dependencies.map {
              case (depOrg, depName, ver, confOpt) =>
                <dependency>
                  <groupId>{depOrg.value}</groupId>
                  <artifactId>{depName.value}</artifactId>
                  <version>{ver}</version>
                  {confOpt.fold[NodeSeq](Nil)(c => <scope>{c}</scope>)}
                </dependency>
            }
          }
        </dependencies>

    print(
      <project xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xmlns="http://maven.apache.org/POM/4.0.0">
        {nodes.result()}
      </project>
    )
  }

  private def addOrUpdate(content: Elem, label: String)(update: Option[Node] =>Node): Elem = {

    // assumes there's at most one child with this label…

    val found = content.child.exists(_.label == label)

    val updatedChildren =
      if (found)
        content.child.map {
          case n if n.label == label =>
            update(Some(n))
          case n =>
            n
        }
      else
        content.child :+ update(None)

    content.copy(
      child = updatedChildren
    )
  }

  def overrideOrganization(organization: Organization, content: Elem): Elem = {

    val content0 = addOrUpdate(content, "groupId") { _ =>
      <groupId>{organization.value}</groupId>
    }

    addOrUpdate(content0, "organization") {
      case Some(elem0: Elem) =>
        addOrUpdate(elem0, "name") { _ =>
          <name>{organization.value}</name>
        }
      case _ =>
        <organization>
          <name>{organization.value}</name>
        </organization>
    }
  }

  def overrideModuleName(name: ModuleName, content: Elem): Elem =
    addOrUpdate(content, "artifactId") { _ =>
      <artifactId>{name.value}</artifactId>
    }

  def overrideVersion(version: String, content: Elem): Elem =
    addOrUpdate(content, "version") { _ =>
      <version>{version}</version>
    }

  def overrideHomepage(url: String, content: Elem): Elem = {

    val content0 = addOrUpdate(content, "url") { _ =>
      <url>{url}</url>
    }

    addOrUpdate(content0, "organization") {
      case Some(elem0: Elem) =>
        addOrUpdate(elem0, "url") { _ =>
          <url>{url}</url>
        }
      case _ =>
        <organization>
          <url>{url}</url>
        </organization>
    }
  }

  def overrideScm(domain: String, path: String, content: Elem): Elem =
    addOrUpdate(content, "scm") {
      case Some(elem0: Elem) =>
        var elem1 = addOrUpdate(elem0, "url") { _ =>
          <url>https://{domain}/{path}</url>
        }
        elem1 = addOrUpdate(elem1, "connection") { _ =>
          <connection>scm:git:https://{domain}/{path}.git</connection>
        }
        addOrUpdate(elem1, "developerConnection") { _ =>
          <developerConnection>scm:git:git@{domain}:{path}.git</developerConnection>
        }
      case _ =>
        <scm>
          <url>https://{domain}/{path}</url>
          <connection>scm:git:https://{domain}/{path}.git</connection>
          <developerConnection>scm:git:git@{domain}:{path}.git</developerConnection>
        </scm>
    }

  def overrideDistributionManagementRepository(id: String, name: String, url: String, content: Elem): Elem =
    addOrUpdate(content, "distributionManagement") {
      case Some(elem0: Elem) =>
        addOrUpdate(elem0, "repository") { _ =>
          <repository>
            <id>{id}</id>
            <name>{name}</name>
            <url>{url}</url>
          </repository>
        }
      case _ =>
        <distributionManagement>
          <repository>
            <id>{id}</id>
            <name>{name}</name>
            <url>{url}</url>
          </repository>
        </distributionManagement>
    }

  def overrideLicenses(licenses: Seq[License], content: Elem): Elem =
    addOrUpdate(content, "licenses") { _ =>
      <licenses>{
        licenses.map { l =>
          <license>
            <name>{l.name}</name>
            <url>{l.url}</url>
            <distribution>repo</distribution>
          </license>
        }
      }</licenses>
    }

  def overrideDevelopers(developers: Seq[Developer], content: Elem): Elem =
    addOrUpdate(content, "developers") { _ =>
      <developers>{
        developers.map { dev =>
          <developer>
            <id>{dev.id}</id>
            <name>{dev.name}</name>
            {
              dev.mail match {
                case None =>
                  <email/>
                case Some(mail) =>
                  <email>{mail}</email>
              }
            }
            <url>{dev.url}</url>
          </developer>
        }
        }</developers>
    }

  def transformDependency(
    content: Elem,
    from: (Organization, ModuleName),
    to: (Organization, ModuleName)
  ): Elem = {

    def adjustGroupArtifactIds(n: Elem): Elem = {

      val orgOpt = n.child.collectFirst {
        case n if n.label == "groupId" => Organization(n.text)
      }
      val nameOpt = n.child.collectFirst {
        case n if n.label == "artifactId" => ModuleName(n.text)
      }

      if (orgOpt.contains(from._1) && nameOpt.contains(from._2))
        n.copy(
          child = n.child.map {
            case n if n.label == "groupId" =>
              <groupId>{to._1.value}</groupId>
            case n if n.label == "artifactId" =>
              <artifactId>{to._2.value}</artifactId>
            case n => n
          }
        )
      else
        n
    }

    // TODO Adjust dependencyManagement section too?

    content.copy(
      child = content.child.map {
        case n: Elem if n.label == "dependencies" =>
          n.copy(
            child = n.child.map {
              case n: Elem if n.label == "dependency" =>
                val n0 = adjustGroupArtifactIds(n)
                n0.copy(
                  child = n0.child.map {
                    case n: Elem if n.label == "exclusions" =>
                      n.copy(
                        child = n.child.map {
                          case n: Elem if n.label == "exclusion" =>
                            adjustGroupArtifactIds(n)
                          case n => n
                        }
                      )
                    case n => n
                  }
                )
              case n => n
            }
          )
        case n => n
      }
    )
  }

  def transformDependencyVersion(
    content: Elem,
    org: Organization,
    name: ModuleName,
    fromVersion: String,
    toVersion: String
  ): Elem = {

    def adjustVersion(n: Elem): Elem = {

      val orgOpt = n.child.collectFirst {
        case n if n.label == "groupId" => Organization(n.text)
      }
      val nameOpt = n.child.collectFirst {
        case n if n.label == "artifactId" => ModuleName(n.text)
      }

      if (orgOpt.contains(org) && nameOpt.contains(name))
        n.copy(
          child = n.child.map {
            case n if n.label == "version" && n.text.trim == fromVersion =>
              <version>{toVersion}</version>
            case n => n
          }
        )
      else
        n
    }

    // TODO Adjust dependencyManagement section too?

    content.copy(
      child = content.child.map {
        case n: Elem if n.label == "dependencies" =>
          n.copy(
            child = n.child.map {
              case n: Elem if n.label == "dependency" =>
                adjustVersion(n)
              case n => n
            }
          )
        case n => n
      }
    )
  }

  def print(elem: Elem): String = {
    val printer = new scala.xml.PrettyPrinter(Int.MaxValue, 2)
    """<?xml version="1.0" encoding="UTF-8"?>""" + '\n' + printer.format(elem)
  }

}
