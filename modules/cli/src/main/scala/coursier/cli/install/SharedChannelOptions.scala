package coursier.cli.install

final case class SharedChannelOptions(

  channel: List[String] = Nil,

  defaultChannels: Boolean = true,
  fileChannels: Boolean = true,

)
