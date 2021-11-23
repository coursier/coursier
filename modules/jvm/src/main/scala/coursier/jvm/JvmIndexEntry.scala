package coursier.jvm

import coursier.cache.ArchiveType
import dataclass.data

@data class JvmIndexEntry(
  os: String,
  architecture: String,
  name: String,
  version: String,
  archiveType: ArchiveType,
  url: String
) {
  def id: String =
    s"$name@$version"
}
