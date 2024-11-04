package coursier.cli.install

import caseapp._
import coursier.cli.options.OptionGroup

// format: off
final case class SharedChannelOptions(

  @Group(OptionGroup.channel)
  @HelpMessage("Channel for apps")
  @ValueDescription("org:name")
    channel: List[String] = Nil,

  @Group(OptionGroup.channel)
  @Hidden
  @HelpMessage("Add default channels")
    defaultChannels: Boolean = true,

  @Group(OptionGroup.channel)
  @HelpMessage("Add contrib channel")
    contrib: Boolean = false,

  @Group(OptionGroup.channel)
  @Hidden
  @HelpMessage("Add channels read from the configuration directory")
    fileChannels: Boolean = true

)
// format: on
