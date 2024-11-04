package coursier.cli.channel

import caseapp._
import coursier.cli.options.{OptionGroup, OutputOptions}

// format: off
@HelpMessage(
  "Manage additional channels, used by coursier to resolve application descriptors.\n" +
  "Those channels are stored in coursier configuration files.\n" +
  "\n" +
  "Examples:\n" +
  "$ cs channel --add io.get-coursier:apps-contrib\n" +
  "$ cs channel --list\n"
)
final case class ChannelOptions(
  @Group(OptionGroup.channel)
  @ExtraName("a")
  @HelpMessage("adds given URL based channels")
    add: List[String] = Nil,
  @Group(OptionGroup.channel)
  @ExtraName("l")
  @HelpMessage("lists down all added channels")
    list: Boolean = false,
  @Recurse
    outputOptions: OutputOptions = OutputOptions()
)
// format: on

object ChannelOptions {
  implicit lazy val parser: Parser[ChannelOptions] = Parser.derive
  implicit lazy val help: Help[ChannelOptions]     = Help.derive
}
