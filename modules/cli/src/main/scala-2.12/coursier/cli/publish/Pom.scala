package coursier.cli.publish

import coursier.core.{Configuration, Type}
import coursier.{ModuleName, Organization}

import scala.collection.mutable
import scala.xml.{Elem, NodeSeq}

object Pom {

  // FIXME There's a lib for that
  final case class License(name: String, url: String)

  object License {
    def apache2: License =
      License("Apache 2.0", "http://opensource.org/licenses/Apache-2.0")
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

  def overrideOrganization(organization: Organization, content: Elem): Elem =
    content.copy(
      child = content.child.map {
        case n if n.label == "groupId" =>
          <groupId>{organization.value}</groupId>
        case elem0: Elem if elem0.label == "organization" =>
          elem0.copy(
            child = elem0.child.map {
              case n if n.label == "name" =>
                <name>{organization.value}</name>
              case n =>
                n
            }
          )
        case n =>
          n
      }
    )
  def overrideModuleName(name: ModuleName, content: Elem): Elem =
    content.copy(
      child = content.child.map {
        case n if n.label == "artifactId" =>
          <artifactId>{name.value}</artifactId>
        case n =>
          n
      }
    )
  def overrideVersion(version: String, content: Elem): Elem =
    content.copy(
      child = content.child.map {
        case n if n.label == "version" =>
          <version>{version}</version>
        case n =>
          n
      }
    )

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

  def print(elem: Elem): String = {
    val printer = new scala.xml.PrettyPrinter(Int.MaxValue, 2)
    """<?xml version="1.0" encoding="UTF-8"?>""" + '\n' + printer.format(elem)
  }

}
