package coursier.cli.options

import caseapp.{Parser, Recurse}

final case class ShoestrapOptions(
  @Recurse
    artifactOptions: ArtifactOptions,
  @Recurse
    options: ShoestrapSpecificOptions
)

object ShoestrapOptions {
  implicit val parser = Parser[ShoestrapOptions]
  implicit val help = caseapp.core.help.Help[ShoestrapOptions]
}
