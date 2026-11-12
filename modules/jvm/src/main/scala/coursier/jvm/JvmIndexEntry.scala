package coursier.jvm

import dataclass.{data, since}

import coursier.cache.ArchiveType

@data(
  deprecatedSetters = true,
  deprecatedSettersMessage = "Use copy instead",
  deprecatedSettersSince = "2.1.25"
) case class JvmIndexEntry(
  os: String,
  architecture: String,
  name: String,
  version: String,
  archiveType: ArchiveType,
  url: String,
  /** Checksums of the archive at `url`, keyed by checksum type, like `sha256` or `sha1`
    *
    * Values are hex-encoded. These come from the indices with checksums (`indices-v2` in
    * coursier/jvm-index), and are empty for the other indices.
    */
  @since
  checksums: Map[String, String] = Map.empty
) {
  def id: String =
    s"$name@$version"
}
