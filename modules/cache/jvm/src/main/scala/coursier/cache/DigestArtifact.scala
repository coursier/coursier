package coursier.cache

import dataclass.data


import java.nio.file.Path

@data case class DigestArtifact(
  digest: String,
  path: Path
)
