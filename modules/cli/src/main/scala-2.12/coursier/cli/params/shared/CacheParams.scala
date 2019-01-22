package coursier.cli.params.shared

import java.io.File

import coursier.CachePolicy

import scala.concurrent.duration.Duration

final case class CacheParams(
  cache: File, // directory, existing or that can be created
  cachePolicies: Seq[CachePolicy], // non-empty
  ttl: Option[Duration],
  parallel: Int, // positive
  checksum: Seq[Option[String]],
  retryCount: Int,
  cacheLocalArtifacts: Boolean,
  followHttpToHttpsRedirections: Boolean
)
