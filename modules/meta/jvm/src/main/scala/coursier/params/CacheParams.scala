package coursier.params

import java.io.File

import coursier.CachePolicy
import coursier.cache.CacheDefaults

import scala.concurrent.duration.Duration

final case class CacheParams(
  cache: File = CacheDefaults.location, // directory, existing or that can be created
  cachePolicies: Seq[CachePolicy] = CachePolicy.default, // non-empty
  ttl: Option[Duration] = CacheDefaults.ttl,
  parallel: Int = CacheDefaults.concurrentDownloadCount, // FIXME Move elsewhere?
  checksum: Seq[Option[String]] = CacheDefaults.checksums,
  retryCount: Int = 1,
  cacheLocalArtifacts: Boolean = false,
  followHttpToHttpsRedirections: Boolean = true
)
