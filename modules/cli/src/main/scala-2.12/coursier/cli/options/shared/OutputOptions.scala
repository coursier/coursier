package coursier.cli.options.shared

import caseapp.{ExtraName => Short, HelpMessage => Help, _}

final case class OutputOptions(

  @Help("Quiet output")
  @Short("q")
    quiet: Boolean = false,

  @Help("Increase verbosity (specify several times to increase more)")
  @Short("v")
    verbose: Int @@ Counter = Tag.of(0),

  @Help("Force display of progress bars")
  @Short("P")
    progress: Boolean = false

) {

  val verbosityLevel = Tag.unwrap(verbose) - (if (quiet) 1 else 0)

}

object OutputOptions {
  implicit val parser = Parser[OutputOptions]
  implicit val help = caseapp.core.help.Help[OutputOptions]
}
