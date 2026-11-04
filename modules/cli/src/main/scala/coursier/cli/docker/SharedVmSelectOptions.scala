package coursier.cli.docker

import caseapp.Name
import caseapp.core.help.Help
import caseapp.core.parser.Parser

// format: off
final case class SharedVmSelectOptions(
  @Name("id") // FIXME Fine in "vm start" and "vm stop" command, less so in "docker *" commands
  @Name("vm") // FIXME Opposite of above?
    vmId: Option[String] = None
) derives Parser, Help
// format: on
