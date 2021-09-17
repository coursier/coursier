package coursier.cli.install

import caseapp.{ExtraName => Short, HelpMessage => Help, _}

// format: off
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
