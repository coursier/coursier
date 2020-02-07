package coursier.install

import coursier.core.Repository
import dataclass.data

@data class Source(
  repositories: Seq[Repository],
  channel: Channel,
  id: String
)
