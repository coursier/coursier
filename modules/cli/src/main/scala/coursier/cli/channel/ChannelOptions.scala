package coursier.cli.channel

import caseapp.{ExtraName => Short, HelpMessage => Help, Recurse}
import coursier.cli.options.OutputOptions

// format: off
@Help(
  "Manage additional channels, used by coursier to resolve application descriptors.\n" +
  "Those channels are stored in coursier configuration files.\n" +
  "\n" +
  "Examples:\n" +
  "$ cs channel --add io.get-coursier:apps-contrib\n" +
  "$ cs channel --list\n"
)
final case class ChannelOptions(
  @Short("a")
  @Help("adds given URL based channels")
    add: List[String] = Nil,
  @Short("l")
  @Help("lists down all added channels")
    list: Boolean = false,
  @Recurse
    outputOptions: OutputOptions = OutputOptions()
)
// format: on
