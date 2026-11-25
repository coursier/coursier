package coursier.cache

import dataclass.data

import java.nio.file.Path

@data(
  deprecatedSetters = true,
  deprecatedSettersMessage = "Use copy instead",
  deprecatedSettersSince = "2.1.25"
) case class DigestArtifact(
  digest: String,
  path: Path
)
