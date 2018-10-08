package coursier.maven

import coursier.core._

final case class MavenSource(
  root: String,
  changing: Option[Boolean] = None,
  /** See doc on MavenRepository */
  sbtAttrStub: Boolean,
  authentication: Option[Authentication]
) extends Artifact.Source {

  import Repository._
  import MavenRepository._

  private def artifacts0(
    dependency: Dependency,
    project: Project,
    overrideClassifiers: Option[Seq[String]]
  ): Seq[(Attributes, Artifact)] = {

    val packagingOpt = project.packagingOpt.filter(_ != Pom.relocatedPackaging)

    val packagingTpeMap = packagingOpt
      .map { packaging =>
        (MavenAttributes.typeDefaultClassifier(packaging), MavenAttributes.typeExtension(packaging)) -> packaging
      }
      .toMap

    def artifactOf(publication: Publication) = {

      val versioning = project
        .snapshotVersioning
        .flatMap(versioning =>
          mavenVersioning(
            versioning,
            publication.classifier,
            MavenAttributes.typeExtension(publication.`type`)
          )
        )

      val path = dependency.module.organization.split('.').toSeq ++ Seq(
        MavenRepository.dirModuleName(dependency.module, sbtAttrStub),
        toBaseVersion(project.actualVersion),
        s"${dependency.module.name}-${versioning getOrElse project.actualVersion}${Some(publication.classifier).filter(_.nonEmpty).map("-" + _).mkString}.${publication.ext}"
      )

      val changing0 = changing.getOrElse(isSnapshot(project.actualVersion))

      val artifact = Artifact(
        root + path.mkString("/"),
        Map.empty,
        Map.empty,
        changing = changing0,
        optional = true,
        authentication = authentication
      )
        .withDefaultChecksums
        .withDefaultSignature

      (publication.attributes, artifact)
    }

    val (_, metadataArtifact) = artifactOf(Publication(dependency.module.name, "pom", "pom", ""))

    def artifactWithExtra(publication: Publication) = {
      val (attr, artifact) = artifactOf(publication)
      (attr, artifact.copy(
        extra = artifact.extra + ("metadata" -> metadataArtifact)
      ))
    }

    lazy val defaultPublications = {

      val packagingPublicationOpt = packagingOpt
        .filter(_ => dependency.attributes.isEmpty)
        .map { packaging =>
          Publication(
            dependency.module.name,
            packaging,
            MavenAttributes.typeExtension(packaging),
            MavenAttributes.typeDefaultClassifier(packaging)
          )
        }

      val type0 = if (dependency.attributes.`type`.isEmpty) "jar" else dependency.attributes.`type`

      val ext = MavenAttributes.typeExtension(type0)

      val classifier =
        if (dependency.attributes.classifier.isEmpty)
          MavenAttributes.typeDefaultClassifier(type0)
        else
          dependency.attributes.classifier

      val tpe = packagingTpeMap.getOrElse(
        (classifier, ext),
        MavenAttributes.classifierExtensionDefaultTypeOpt(classifier, ext).getOrElse(ext)
      )

      val pubs = packagingPublicationOpt.toSeq :+
        Publication(
          dependency.module.name,
          tpe,
          ext,
          classifier
        )

      pubs.distinct
    }

    overrideClassifiers
      .fold(defaultPublications) { classifiers =>
        classifiers.flatMap { classifier =>
          if (classifier == dependency.attributes.classifier)
            defaultPublications
          else {
            val ext = "jar"
            val tpe = packagingTpeMap.getOrElse(
              (classifier, ext),
              MavenAttributes.classifierExtensionDefaultTypeOpt(classifier, ext).getOrElse(ext)
            )

            Seq(
              Publication(
                dependency.module.name,
                tpe,
                ext,
                classifier
              )
            )
          }
        }
      }
      .map(artifactWithExtra)
  }

  def artifacts(
    dependency: Dependency,
    project: Project,
    overrideClassifiers: Option[Seq[String]]
  ): Seq[(Attributes, Artifact)] =
    if (project.packagingOpt.toSeq.contains(Pom.relocatedPackaging))
      Nil
    else
      artifacts0(dependency, project, overrideClassifiers)

}
