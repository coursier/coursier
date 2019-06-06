package coursier.cli.publish.options

final case class SignatureOptions(
  gpg: Option[Boolean] = None,
  gpgKey: Option[String] = None
)
