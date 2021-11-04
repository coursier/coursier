package coursier.cli.options

import caseapp.{ExtraName => Short, HelpMessage => Help, _}

// format: off
final case class OutputOptions(

  @Group("Verbosity")
  @Help("Quiet output")
  @Short("q")
    quiet: Int @@ Counter = Tag.of(0),

  @Group("Verbosity")
  @Help("Increase verbosity (specify several times to increase more)")
  @Short("v")
    verbose: Int @@ Counter = Tag.of(0),

  @Group("Verbosity")
  @Help("Force display of progress bars")
  @Short("P")
    progress: Boolean = false,

  @Group("Verbosity")
  @Hidden
  @Help("Log changing artifacts")
    logChanging: Boolean = false,

  @Group("Verbosity")
  @Hidden
  @Help("Log app channel or JVM index version")
  @Short("log-index-version")
  @Short("log-jvm-index-version")
    logChannelVersion: Boolean = false

)
// format: on

object OutputOptions {
  implicit val parser = Parser[OutputOptions]
  implicit val help   = caseapp.core.help.Help[OutputOptions]
}
