package coursier.cli.install

import caseapp.{HelpMessage => Help, ValueDescription => Value, _}

// format: off
final case class SharedChannelOptions(

  @Help("Channel for apps")
  @Value("org:name")
    channel: List[String] = Nil,

  @Help("Add default channels")
    defaultChannels: Boolean = true,

  @Help("Add contrib channel")
    contrib: Boolean = false,

  @Help("Add channels read from the configuration directory")
    fileChannels: Boolean = true

)
// format: on
