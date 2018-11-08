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

  git: Boolean = false

)

object MetadataOptions {
  implicit val parser = Parser[MetadataOptions]
  implicit val help = caseapp.core.help.Help[MetadataOptions]
}
