package coursier.cli.options

import caseapp._

// format: off
final case class OutputOptions(

  @Group(OptionGroup.verbosity)
  @HelpMessage("Quiet output")
  @ExtraName("q")
    quiet: Int @@ Counter = Tag.of(0),

  @Group(OptionGroup.verbosity)
  @HelpMessage("Increase verbosity (specify several times to increase more)")
  @ExtraName("v")
    verbose: Int @@ Counter = Tag.of(0),

  @Group(OptionGroup.verbosity)
  @HelpMessage("Force display of progress bars")
  @ExtraName("P")
    progress: Boolean = false,

  @Group(OptionGroup.verbosity)
  @Hidden
  @HelpMessage("Log changing artifacts")
    logChanging: Boolean = false,

  @Group(OptionGroup.verbosity)
  @Hidden
  @HelpMessage("Log app channel or JVM index version")
  @ExtraName("log-index-version")
  @ExtraName("log-jvm-index-version")
    logChannelVersion: Boolean = false

)
// format: on

object OutputOptions {
  lazy val parser: Parser[OutputOptions]                           = Parser.derive
  implicit lazy val parserAux: Parser.Aux[OutputOptions, parser.D] = parser
  implicit lazy val help: Help[OutputOptions]                      = Help.derive
}
