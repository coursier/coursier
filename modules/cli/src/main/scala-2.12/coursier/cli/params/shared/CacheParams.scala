package coursier.cli.params.shared

import java.io.File

import cats.data.{Validated, ValidatedNel}
import cats.implicits._
import coursier.{Cache, CacheParse, CachePolicy}
import coursier.cli.options.shared.CacheOptions

import scala.concurrent.duration.Duration

final case class CacheParams(
  cache: File, // directory, existing or that can be created
  cachePolicies: Seq[CachePolicy], // non-empty
  ttl: Option[Duration],
  parallel: Int, // positive
  checksum: Seq[Option[String]],
  retryCount: Int,
  cacheLocalArtifacts: Boolean
)

object CacheParams {
  def apply(options: CacheOptions): ValidatedNel[String, CacheParams] = {

    val cache = new File(options.cache)

    val cachePoliciesV =
      if (options.mode.isEmpty)
        Validated.validNel(CachePolicy.default)
      else
        CacheParse.cachePolicies(options.mode).either match {
          case Right(cp) =>
            Validated.validNel(cp)
          case Left(errors) =>
            Validated.invalidNel(
              s"Error parsing modes:\n${errors.map("  "+_).mkString("\n")}"
            )
        }

    val ttlV =
      if (options.ttl.isEmpty)
        Validated.validNel(Cache.defaultTtl)
      else
        try Validated.validNel(Some(Duration(options.ttl)))
        catch {
          case e: NumberFormatException =>
            Validated.invalidNel(s"Parsing TTL: ${e.getMessage}")
        }

    val parallelV =
      if (options.parallel > 0)
        Validated.validNel(options.parallel)
      else
        Validated.invalidNel(s"Parallel must be > 0 (got ${options.parallel})")

    val checksumV = {

      // TODO Validate those more thoroughly

      val splitChecksumArgs = options.checksum.flatMap(_.split(',')).filter(_.nonEmpty)

      val res =
        if (splitChecksumArgs.isEmpty)
          Cache.defaultChecksums
        else
          splitChecksumArgs.map {
            case none if none.toLowerCase == "none" => None
            case sumType => Some(sumType)
          }

      Validated.validNel(res)
    }

    val retryCountV =
      if (options.retryCount > 0)
        Validated.validNel(options.retryCount)
      else
        Validated.invalidNel(s"Retry count must be > 0 (got ${options.retryCount})")

    val cacheLocalArtifacts = options.cacheFileArtifacts

    (cachePoliciesV, ttlV, parallelV, checksumV, retryCountV).mapN {
      (cachePolicy, ttl, parallel, checksum, retryCount) =>
        CacheParams(
          cache,
          cachePolicy,
          ttl,
          parallel,
          checksum,
          retryCount,
          cacheLocalArtifacts
        )
    }
  }
}
