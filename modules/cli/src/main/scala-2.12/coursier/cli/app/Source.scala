package coursier.cli.app

import coursier.core.{Module, Repository}

final case class Source(
  repositories: Seq[Repository],
  channel: Module,
  id: String
)
