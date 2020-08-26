package coursier.cli.publish.options

import caseapp._

final case class SinglePackageOptions(

  jar: Option[String] = None,

  @Name("P")
    pom: Option[String] = None,

  @Name("A")
  @ValueDescription("classifier:/path/to/file")
    artifact: List[String] = Nil,

  @HelpMessage("Force creation of a single package (default: true if --jar or --pom or --artifact specified, else false)")
    `package`: Option[Boolean] = None

)

object SinglePackageOptions {
  implicit val parser = Parser[SinglePackageOptions]
  implicit val help = caseapp.core.help.Help[SinglePackageOptions]
}
