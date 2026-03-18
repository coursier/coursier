package coursier.cache

import dataclass.data

import java.nio.file.Path

@data class DigestArtifact(
  digest: String,
  path: Path
)
