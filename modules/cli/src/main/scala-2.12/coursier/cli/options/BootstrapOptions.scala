package coursier.cli.options

import caseapp.{Parser, Recurse}
import coursier.cli.options.shared.ArtifactOptions

final case class BootstrapOptions(
  @Recurse
    artifactOptions: ArtifactOptions = ArtifactOptions(),
  @Recurse
    options: BootstrapSpecificOptions = BootstrapSpecificOptions()
)

object BootstrapOptions {
  implicit val parser = Parser[BootstrapOptions]
  implicit val help = caseapp.core.help.Help[BootstrapOptions]
}
