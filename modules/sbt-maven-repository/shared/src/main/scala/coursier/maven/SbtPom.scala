package coursier.maven

import coursier.core._
import coursier.core.Validation._
import coursier.util.Traverse.TraverseOps

import scala.collection.compat._

object SbtPom extends Pom {
  import coursier.util.Xml._
  type ModuleVersion = (Module, String)

  override def project(
    finalProjModule: Module,
    finalVersion: String,
    dependencies: Seq[(Configuration, Dependency)],
    parent: Option[(Module, String)],
    dependencyManagement: Seq[(Configuration, Dependency)],
    properties: Seq[(String, String)],
    profiles: Seq[Profile],
    packaging: Option[Type],
    relocated: Boolean,
    info: Info
  ): Either[String, Project] =
    for {
      extraAttrs <- properties
        .collectFirst { case ("extraDependencyAttributes", s) => extraAttributes(s) }
        .getOrElse(Right(Map.empty[ModuleVersion, Map[String, String]]))
    } yield {
      val adaptedDependencies = dependencies.map {
        case (config, dep0) =>
          val dep = extraAttrs.get(dep0.moduleVersion).fold(dep0) { attrs =>
            // For an sbt plugin, we remove the suffix from the name and we add the sbtVersion
            // and scalaVersion attributes.
            val moduleWithAttrs = getSbtCrossVersion(attrs)
              .fold(dep0.module) { sbtCrossVersion =>
                val sttripedName = dep0.module.name.value.stripSuffix(sbtCrossVersion)
                dep0.module.withName(ModuleName(sttripedName))
              }
              .withAttributes(attrs)
            dep0.withModule(moduleWithAttrs)
          }
          config -> dep
      }

      Project(
        finalProjModule,
        finalVersion,
        adaptedDependencies,
        Map.empty,
        parent,
        dependencyManagement,
        properties,
        profiles,
        None,
        None,
        packaging,
        relocated,
        None,
        Nil,
        info
      )
    }

  private[coursier] def getSbtCrossVersion(attributes: Map[String, String]): Option[String] =
    for {
      sbtVersion   <- attributes.get("sbtVersion")
      scalaVersion <- attributes.get("scalaVersion")
    } yield s"_${scalaVersion}_$sbtVersion"

  private def extraAttributes(s: String)
    : Either[String, Map[ModuleVersion, Map[String, String]]] = {
    val lines = s.split('\n').toSeq.map(_.trim).filter(_.nonEmpty)

    lines.foldLeft[Either[String, Map[ModuleVersion, Map[String, String]]]](Right(Map.empty)) {
      case (acc, line) =>
        for {
          modVers <- acc
          modVer  <- extraAttribute(line)
        } yield modVers + modVer
    }
  }

  private def extraAttribute(s: String): Either[String, (ModuleVersion, Map[String, String])] = {
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
          Left(
            s"Malformed attributes ${malformed.map("'" + _ + "'").mkString(", ")} in extra attributes '$s'"
          )
      }
      else
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
      org     <- attrFrom(attrs, extraAttributeOrg).map(Organization(_))
      name    <- attrFrom(attrs, extraAttributeName).map(ModuleName(_))
      version <- attrFrom(attrs, extraAttributeVersion)
    } yield {
      val remainingAttrs = attrs.view.filterKeys(!extraAttributeBase(_)).toMap
      ((Module(org, name, Map.empty), version), remainingAttrs)
    }
  }
}
