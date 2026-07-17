package coursier.cache


import java.nio.file.Path

final case class DigestArtifact(
  digest: String,
  path: Path
)
