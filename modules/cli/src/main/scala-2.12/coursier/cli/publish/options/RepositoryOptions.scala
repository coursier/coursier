package coursier.cli.publish.options

import caseapp._

final case class RepositoryOptions(

  @Name("r")
  @Name("repo")
  @Name("dest")
    repository: Option[String] = None,

  @HelpMessage("Repository to read maven-metadata.xml files from")
    readFrom: Option[String] = None,

  auth: Option[String] = None,

  sonatype: Option[Boolean] = None

) {
  override def toString: String =
    Seq(
      repository,
      "****",
      sonatype
    ).mkString("RepositoryOptions(", ", ", ")")
}

object RepositoryOptions {
  implicit val parser = Parser[RepositoryOptions]
  implicit val help = caseapp.core.help.Help[RepositoryOptions]
}
