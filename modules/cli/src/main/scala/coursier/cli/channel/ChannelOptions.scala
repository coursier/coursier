package coursier.cli.channel

import caseapp.{ExtraName => Short, HelpMessage => Help, _}
import coursier.cli.options.OutputOptions

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
