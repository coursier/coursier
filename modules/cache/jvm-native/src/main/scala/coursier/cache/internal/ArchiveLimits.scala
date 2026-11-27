package coursier.cache.internal

import scala.util.Try

import coursier.util.EnvEntry

/** Limits on the size and number of entries of the content of archives
  *
  * Guards against archive bombs, small archives that expand to huge sizes or numbers of files. Used
  * both when extracting archives (`UnArchiver` in coursier-archive-cache) and when decompressing
  * them to check their integrity ([[ArchiveIntegrity]]).
  */
private[coursier] object ArchiveLimits {

  val maxSizeEntry: EnvEntry =
    EnvEntry("COURSIER_ARCHIVE_MAX_SIZE", "coursier.archive.max-size")
  val maxEntriesEntry: EnvEntry =
    EnvEntry("COURSIER_ARCHIVE_MAX_ENTRIES", "coursier.archive.max-entries")

  /** Default maximum size of the content of an archive, in bytes (8 GiB)
    *
    * JDK or GraalVM archives are several hundreds of MB once extracted, so that leaves a lot of
    * room, while preventing archive bombs from filling the disk.
    */
  def maxSizeFallback: Long = 8L * 1024L * 1024L * 1024L

  /** Default maximum number of entries of an archive */
  def maxEntriesFallback: Long = 1000000L

  // accepts a number of bytes, optionally followed by a K, M, G, or T suffix (powers of 1024)
  private def parseSize(input: String): Option[Long] = {
    val s = input.trim
    val (num, factor) = s.lastOption.map(_.toUpper) match {
      case Some('K') => (s.init, 1024L)
      case Some('M') => (s.init, 1024L * 1024L)
      case Some('G') => (s.init, 1024L * 1024L * 1024L)
      case Some('T') => (s.init, 1024L * 1024L * 1024L * 1024L)
      case _         => (s, 1L)
    }
    Try(Math.multiplyExact(num.trim.toLong, factor)).toOption
  }

  /** Reads a limit from `entry`, defaulting to `fallback` */
  def readLimit(entry: EnvEntry, fallback: Long): Long = {
    val values = entry.read()
    values.env.map(v => (v, s"environment variable ${entry.envName}"))
      .orElse(values.prop.map(v => (v, s"Java property ${entry.propName}")))
      .fold(fallback) {
        case (value, from) =>
          parseSize(value).getOrElse {
            throw new IllegalArgumentException(s"Invalid value '$value' for $from")
          }
      }
  }

  /** Maximum size of the content of an archive, in bytes
    *
    * Read from the `COURSIER_ARCHIVE_MAX_SIZE` environment variable or the
    * `coursier.archive.max-size` Java property, as a number of bytes, optionally followed by K, M,
    * G, or T (like `32G`). 0 disables the limit.
    */
  def maxSize: Long =
    readLimit(maxSizeEntry, maxSizeFallback)

  /** Maximum number of entries of an archive
    *
    * Read from the `COURSIER_ARCHIVE_MAX_ENTRIES` environment variable or the
    * `coursier.archive.max-entries` Java property. 0 disables the limit.
    */
  def maxEntries: Long =
    readLimit(maxEntriesEntry, maxEntriesFallback)

  /** Message of the errors reported when a limit is exceeded
    *
    * @param action
    *   what was being done with the archive, like "Extracting /path/to/archive.zip"
    * @param what
    *   the limit, like "extracted size"
    */
  def exceededMessage(action: String, what: String, limit: Long, entry: EnvEntry): String =
    s"$action exceeds the maximum $what ($limit). " +
      s"Set ${entry.envName} in the environment, or the ${entry.propName} Java property, " +
      "to change that limit (0 to disable it)."
}
