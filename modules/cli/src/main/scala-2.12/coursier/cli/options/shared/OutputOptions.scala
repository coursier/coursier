package coursier.cli.options.shared

import caseapp.{ExtraName => Short, HelpMessage => Help, _}

final case class OutputOptions(

  @Help("Quiet output")
  @Short("q")
    quiet: Int @@ Counter = Tag.of(0),

  @Help("Increase verbosity (specify several times to increase more)")
  @Short("v")
    verbose: Int @@ Counter = Tag.of(0),

  @Help("Force display of progress bars")
  @Short("P")
    progress: Boolean = false,

  @Help("Force printing / generating results, even if errored")
  @Short("f")
    force: Boolean = false

) {

  val verbosityLevel = Tag.unwrap(verbose) - Tag.unwrap(quiet)

}

object OutputOptions {
  implicit val parser = Parser[OutputOptions]
  implicit val help = caseapp.core.help.Help[OutputOptions]
}
