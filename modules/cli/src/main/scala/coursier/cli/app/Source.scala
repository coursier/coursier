package coursier.cli.app

import coursier.cli.install.Channel
import coursier.core.Repository

final case class Source(
  repositories: Seq[Repository],
  channel: Channel,
  id: String
)
