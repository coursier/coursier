package coursier.internal

import coursier.Repositories
import coursier.core.Repository
import coursier.ivy.IvyRepository
import coursier.maven.MavenRepository
import coursier.parse.StandardRepository
import coursier.parse.StandardRepository.syntax._

object SharedRepositoryParser {

  def repository(s: String): Either[String, Repository] =
    repositoryAsStandard(s).map(_.repository)

  def repositoryAsStandard(s: String): Either[String, StandardRepository] =
    if (s == "central")
      Right(Repositories.central.asStandard)
    else if (s.startsWith("sonatype:"))
      Right(Repositories.sonatype(s.stripPrefix("sonatype:")).asStandard)
    else if (s.startsWith("sonatype-s01:"))
      Right(Repositories.sonatypeS01(s.stripPrefix("sonatype-s01:")).asStandard)
    else if (s.startsWith("bintray:")) {
      val s0 = s.stripPrefix("bintray:")
      val id =
        if (s.contains("/")) s0
        else s0 + "/maven"

      Right(Repositories.bintray(id).asStandard)
    }
    else if (s.startsWith("bintray-ivy:"))
      Right(Repositories.bintrayIvy(s.stripPrefix("bintray-ivy:")).asStandard)
    else if (s.startsWith("typesafe:ivy-"))
      Right(Repositories.typesafeIvy(s.stripPrefix("typesafe:ivy-")).asStandard)
    else if (s.startsWith("typesafe:"))
      Right(Repositories.typesafe(s.stripPrefix("typesafe:")).asStandard)
    else if (s.startsWith("sbt-maven:"))
      Right(Repositories.sbtMaven(s.stripPrefix("sbt-maven:")).asStandard)
    else if (s.startsWith("sbt-plugin:"))
      Right(Repositories.sbtPlugin(s.stripPrefix("sbt-plugin:")).asStandard)
    else if (s == "scala-integration" || s == "scala-nightlies")
      Right(Repositories.scalaIntegration.asStandard)
    else if (s.startsWith("ivy:")) {
      val s0     = s.stripPrefix("ivy:")
      val sepIdx = s0.indexOf('|')
      if (sepIdx < 0)
        IvyRepository.parse(s0).map(_.asStandard)
      else {
        val mainPart     = s0.substring(0, sepIdx)
        val metadataPart = s0.substring(sepIdx + 1)
        IvyRepository.parse(mainPart, Some(metadataPart)).map(_.asStandard)
      }
    }
    else if (s == "jitpack")
      Right(Repositories.jitpack.asStandard)
    else if (s == "clojars")
      Right(Repositories.clojars.asStandard)
    else if (s == "jcenter")
      Right(Repositories.jcenter.asStandard)
    else if (s == "google")
      Right(Repositories.google.asStandard)
    else if (s == "gcs")
      Right(Repositories.centralGcs.asStandard)
    else if (s == "gcs-eu")
      Right(Repositories.centralGcsEu.asStandard)
    else if (s == "gcs-asia")
      Right(Repositories.centralGcsAsia.asStandard)
    else if (s.startsWith("apache:"))
      Right(Repositories.apache(s.stripPrefix("apache:")).asStandard)
    else
      Right(MavenRepository(s).asStandard)

}
