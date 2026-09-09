package coursier.install

import dataclass.data

import coursier.core.Repository

@data(
  deprecatedSetters = true,
  deprecatedSettersMessage = "Use copy instead",
  deprecatedSettersSince = "2.1.25"
) case class Source(
  repositories: Seq[Repository],
  channel: Channel,
  id: String
)
