package coursier.cli.options.shared

import caseapp.{ExtraName => Short, HelpMessage => Help, ValueDescription => Value, _}
import coursier.cache.CacheDefaults

final case class CacheOptions(

  @Help("Cache directory (defaults to environment variable COURSIER_CACHE, or ~/.cache/coursier/v1 on Linux and ~/Library/Caches/Coursier/v1 on Mac)")
    cache: String = CacheDefaults.location.toString,

  @Help("Download mode (default: missing, that is fetch things missing from cache)")
  @Value("offline|update-changing|update|missing|force")
  @Short("m")
    mode: String = "",

  @Help("TTL duration (e.g. \"24 hours\")")
  @Value("duration")
  @Short("l")
    ttl: String = "",

  @Help("Maximum number of parallel downloads (default: 6)")
  @Short("n")
    parallel: Int = 6,

  @Help("Checksums")
  @Value("checksum1,checksum2,... - end with none to allow for no checksum validation if none are available")
    checksum: List[String] = Nil,

  @Help("Retry limit for Checksum error when fetching a file")
    retryCount: Int = 1,

  @Help("Flag that specifies if a local artifact should be cached.")
  @Short("cfa")
    cacheFileArtifacts: Boolean = false,

  @Help("Whether to follow http to https redirections")
    followHttpToHttpsRedirect: Boolean = true

)

object CacheOptions {
  implicit val parser = Parser[CacheOptions]
  implicit val help = caseapp.core.help.Help[CacheOptions]
}
