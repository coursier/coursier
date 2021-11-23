package coursier.cli.install

import caseapp.{ExtraName => Short, HelpMessage => Help, _}
import coursier.cli.options.OptionGroup

// format: off
@ArgsName("app-name*")
@Help(
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
  @Short("dir")
    installDir: Option[String] = None,

  @Group(OptionGroup.uninstall)
    all: Boolean = false,

  @Group(OptionGroup.uninstall)
  @Help("Quiet output")
  @Short("q")
    quiet: Int @@ Counter = Tag.of(0),

  @Group(OptionGroup.uninstall)
  @Help("Increase verbosity (specify several times to increase more)")
  @Short("v")
    verbose: Int @@ Counter = Tag.of(0)

)
// format: on
