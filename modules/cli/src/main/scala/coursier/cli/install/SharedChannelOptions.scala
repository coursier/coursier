package coursier.cli.install

import caseapp.{HelpMessage => Help, ValueDescription => Value, _}

// format: off
final case class SharedChannelOptions(

  @Group("App channel")
  @Help("Channel for apps")
  @Value("org:name")
    channel: List[String] = Nil,

  @Group("App channel")
  @Hidden
  @Help("Add default channels")
    defaultChannels: Boolean = true,

  @Group("App channel")
  @Help("Add contrib channel")
    contrib: Boolean = false,

  @Group("App channel")
  @Hidden
  @Help("Add channels read from the configuration directory")
    fileChannels: Boolean = true

)
// format: on
