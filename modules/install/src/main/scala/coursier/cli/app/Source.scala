package coursier.cli.app

import coursier.core.Repository

final case class Source(
  repositories: Seq[Repository],
  channel: Channel,
  id: String
)
