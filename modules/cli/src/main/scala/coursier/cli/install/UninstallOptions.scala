package coursier.cli.install

import caseapp.{ExtraName => Short, HelpMessage => Help, _}

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

  @Short("dir")
    installDir: Option[String] = None,

  all: Boolean = false,

  @Help("Quiet output")
  @Short("q")
    quiet: Int @@ Counter = Tag.of(0),

  @Help("Increase verbosity (specify several times to increase more)")
  @Short("v")
    verbose: Int @@ Counter = Tag.of(0)

)
// format: on
