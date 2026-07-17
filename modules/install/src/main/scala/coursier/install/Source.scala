package coursier.install

import coursier.core.Repository

final case class Source(
  repositories: Seq[Repository],
  channel: Channel,
  id: String
)
