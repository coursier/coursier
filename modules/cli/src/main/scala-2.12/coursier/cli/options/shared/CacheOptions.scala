package coursier.cli.options.shared

import java.io.File

import caseapp.{ExtraName => Short, HelpMessage => Help, ValueDescription => Value, _}
import cats.data.{Validated, ValidatedNel}
import cats.implicits._
import coursier.cache.{CacheDefaults, CachePolicy}
import coursier.params.CacheParams
import coursier.parse.CachePolicyParser

import scala.concurrent.duration.Duration

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

) {

  def params: ValidatedNel[String, CacheParams] = {

    val cache0 = new File(cache)

    val cachePoliciesV =
      if (mode.isEmpty)
        Validated.validNel(CachePolicy.default)
      else
        CachePolicyParser.cachePolicies(mode).either match {
          case Right(cp) =>
            Validated.validNel(cp)
          case Left(errors) =>
            Validated.invalidNel(
              s"Error parsing modes:\n${errors.map("  "+_).mkString("\n")}"
            )
        }

    val ttlV =
      if (ttl.isEmpty)
        Validated.validNel(CacheDefaults.ttl)
      else
        try Validated.validNel(Some(Duration(ttl)))
        catch {
          case e: NumberFormatException =>
            Validated.invalidNel(s"Parsing TTL: ${e.getMessage}")
        }

    val parallelV =
      if (parallel > 0)
        Validated.validNel(parallel)
      else
        Validated.invalidNel(s"Parallel must be > 0 (got $parallel)")

    val checksumV = {

      // TODO Validate those more thoroughly

      val splitChecksumArgs = checksum.flatMap(_.split(',').toSeq).filter(_.nonEmpty)

      val res =
        if (splitChecksumArgs.isEmpty)
          CacheDefaults.checksums
        else
          splitChecksumArgs.map {
            case none if none.toLowerCase == "none" => None
            case sumType => Some(sumType)
          }

      Validated.validNel(res)
    }

    val retryCountV =
      if (retryCount > 0)
        Validated.validNel(retryCount)
      else
        Validated.invalidNel(s"Retry count must be > 0 (got $retryCount)")

    (cachePoliciesV, ttlV, parallelV, checksumV, retryCountV).mapN {
      (cachePolicy, ttl, parallel, checksum, retryCount) =>
        CacheParams()
          .withCacheLocation(cache0)
          .withCachePolicies(cachePolicy)
          .withTtl(ttl)
          .withParallel(parallel)
          .withChecksum(checksum)
          .withRetryCount(retryCount)
          .withCacheLocalArtifacts(cacheFileArtifacts)
          .withFollowHttpToHttpsRedirections(followHttpToHttpsRedirect)
    }
  }
}

object CacheOptions {
  implicit val parser = Parser[CacheOptions]
  implicit val help = caseapp.core.help.Help[CacheOptions]
}
