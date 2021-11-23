package coursier.cli.install

import caseapp.{HelpMessage => Help, ValueDescription => Value, _}
import coursier.cli.options.OptionGroup

// format: off
final case class SharedChannelOptions(

  @Group(OptionGroup.channel)
  @Help("Channel for apps")
  @Value("org:name")
    channel: List[String] = Nil,

  @Group(OptionGroup.channel)
  @Hidden
  @Help("Add default channels")
    defaultChannels: Boolean = true,

  @Group(OptionGroup.channel)
  @Help("Add contrib channel")
    contrib: Boolean = false,

  @Group(OptionGroup.channel)
  @Hidden
  @Help("Add channels read from the configuration directory")
    fileChannels: Boolean = true

)
// format: on
