package coursier.cli.options

final case class EnvOptions(
  env: Boolean = false,
  setup: Boolean = false,
  userHome: Option[String] = None
)
