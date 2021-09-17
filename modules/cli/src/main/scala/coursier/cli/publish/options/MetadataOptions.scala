package coursier.cli.publish.options

import caseapp._

// format: off
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
    git: Option[Boolean] = None,

  mavenMetadata: Option[Boolean] = None

)
// format: on

object MetadataOptions {
  implicit val parser = Parser[MetadataOptions]
  implicit val help   = caseapp.core.help.Help[MetadataOptions]
}
