package coursier.cli.publish.options

import caseapp._

final case class MetadataOptions(

  @Name("org")
  @Name("O")
    organization: Option[String] = None,

  @Name("N")
    name: Option[String] = None,

  @Name("V")
    version: Option[String] = None,

  @Name("dep")
  @Name("d")
    dependency: List[String] = Nil,

  license: List[String] = Nil,

  home: Option[String] = None,

  @HelpMessage("Read metadata from git")
    git: Option[Boolean] = None

)

object MetadataOptions {
  implicit val parser = Parser[MetadataOptions]
  implicit val help = caseapp.core.help.Help[MetadataOptions]
}
