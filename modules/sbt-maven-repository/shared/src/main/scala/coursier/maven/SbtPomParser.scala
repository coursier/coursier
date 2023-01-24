package coursier.maven

import coursier.core._
import coursier.core.Validation._

import scala.collection.mutable.ListBuffer

final class SbtPomParser extends PomParser {
  override protected def project(
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
  ): Either[String, Project] = SbtPom.project(
    finalProjModule,
    finalVersion,
    dependencies,
    parent,
    dependencyManagement,
    properties,
    profiles,
    packaging,
    relocated,
    info
  )
}
