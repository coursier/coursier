package coursier.internal

import coursier.Repositories
import coursier.core.Repository
import coursier.ivy.IvyRepository
import coursier.maven.MavenRepository

object SharedRepositoryParser {

  def repository(s: String): Either[String, Repository] =
    if (s == "central")
      Right(Repositories.central)
    else if (s.startsWith("sonatype:"))
      Right(Repositories.sonatype(s.stripPrefix("sonatype:")))
    else if (s.startsWith("bintray:"))
      Right(Repositories.bintray(s.stripPrefix("bintray:")))
    else if (s.startsWith("bintray-ivy:"))
      Right(Repositories.bintrayIvy(s.stripPrefix("bintray-ivy:")))
    else if (s.startsWith("typesafe:ivy-"))
      Right(Repositories.typesafeIvy(s.stripPrefix("typesafe:ivy-")))
    else if (s.startsWith("typesafe:"))
      Right(Repositories.typesafe(s.stripPrefix("typesafe:")))
    else if (s.startsWith("sbt-maven:"))
      Right(Repositories.sbtMaven(s.stripPrefix("sbt-maven:")))
    else if (s.startsWith("sbt-plugin:"))
      Right(Repositories.sbtPlugin(s.stripPrefix("sbt-plugin:")))
    else if (s.startsWith("ivy:"))
      IvyRepository.parse(s.stripPrefix("ivy:"))
    else if (s == "jitpack")
      Right(Repositories.jitpack)
    else
      Right(MavenRepository(s))

}
