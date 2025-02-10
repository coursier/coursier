package coursier.cli.docker

import caseapp.Recurse
import caseapp.core.help.Help
import caseapp.core.parser.Parser
import coursier.cli.options.CacheOptions
import coursier.cli.options.OutputOptions
import caseapp.HelpMessage

// format: off
final case class VmStartOptions(
  @Recurse
    cacheOptions: CacheOptions = CacheOptions(),
  @Recurse
    outputOptions: OutputOptions = OutputOptions(),
  @Recurse
    sharedVmSelectOptions: SharedVmSelectOptions = SharedVmSelectOptions(),
  @HelpMessage("Memory amount, like \"4g\" (passed via -memory to qemu)")
    memory: Option[String] = None,
  @HelpMessage("CPU count, like \"4\", or \"*\" for as many as the machine has (passed via -cpu to qemu)")
    cpu: Option[String] = None,
  @HelpMessage("Name of the user to create in the VM")
    user: Option[String] = None,
  @HelpMessage("Enable virtualization (if not supported by your system, this needs to be explicitly disabled with --virtualization=false)")
    virtualization: Option[Boolean] = None
)
// format: on

object VmStartOptions {
  implicit lazy val parser: Parser[VmStartOptions] = Parser.derive
  implicit lazy val help: Help[VmStartOptions]     = Help.derive
}
