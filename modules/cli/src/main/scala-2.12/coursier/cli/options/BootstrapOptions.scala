package coursier.cli.options

import caseapp.{Parser, Recurse}
import coursier.cli.options.shared.ArtifactOptions

// format: off
final case class BootstrapOptions(
  @Recurse
    artifactOptions: ArtifactOptions = ArtifactOptions(),
  @Recurse
    options: BootstrapSpecificOptions = BootstrapSpecificOptions()
)
// format: on

object BootstrapOptions {
  implicit val parser = Parser[BootstrapOptions]
  implicit val help = caseapp.core.help.Help[BootstrapOptions]
}
