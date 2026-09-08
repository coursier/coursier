package coursier.install

import dataclass.data

import coursier.core.Repository

@data case class Source(
  repositories: Seq[Repository],
  channel: Channel,
  id: String
)
