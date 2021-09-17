package coursier.cli.publish.options

// format: off
final case class SignatureOptions(
  gpg: Option[Boolean] = None,
  gpgKey: Option[String] = None
)
// format: on
