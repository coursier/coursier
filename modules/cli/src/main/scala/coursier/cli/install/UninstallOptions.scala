package coursier.cli.install

import caseapp._
import coursier.cli.options.OptionGroup

// format: off
@ArgsName("app-name*")
@HelpMessage(
  "Uninstall one or more applications.\n" +
  "The given name must be the application executable name, which may differ from the descriptor name.\n" +
  "\n" +
  "Examples:\n" +
  "$ cs uninstall amm\n" +
  "$ cs uninstall bloop scalafix\n" +
  "$ cs uninstall --all\n"
)
final case class UninstallOptions(

  @Group(OptionGroup.uninstall)
  @ExtraName("dir")
    installDir: Option[String] = None,

  @Group(OptionGroup.uninstall)
    all: Boolean = false,

  @Group(OptionGroup.uninstall)
  @HelpMessage("Quiet output")
  @ExtraName("q")
    quiet: Int @@ Counter = Tag.of(0),

  @Group(OptionGroup.uninstall)
  @HelpMessage("Increase verbosity (specify several times to increase more)")
  @ExtraName("v")
    verbose: Int @@ Counter = Tag.of(0)

)
// format: on
