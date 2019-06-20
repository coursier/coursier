package coursier.cli.options

import java.io.File

import caseapp.{ExtraName => Short, HelpMessage => Help, ValueDescription => Value, _}
import cats.data.{NonEmptyList, Validated, ValidatedNel}
import cats.implicits._
import coursier.cache.CacheDefaults
import coursier.credentials.FileCredentials
import coursier.params.CacheParams
import coursier.parse.{CachePolicyParser, CredentialsParser}

import scala.concurrent.duration.Duration

final case class CacheOptions(

  @Help("Cache directory (defaults to environment variable COURSIER_CACHE, or ~/.cache/coursier/v1 on Linux and ~/Library/Caches/Coursier/v1 on Mac)")
    cache: Option[String] = None,

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

  @Help("Checksum types to check - end with none to allow for no checksum validation if no checksum is available, example: SHA-256,SHA-1,none")
  @Value("checksum1,checksum2,...")
    checksum: List[String] = Nil,

  @Help("Retry limit for Checksum error when fetching a file")
    retryCount: Int = 1,

  @Help("Flag that specifies if a local artifact should be cached.")
  @Short("cfa")
    cacheFileArtifacts: Boolean = false,

  @Help("Whether to follow http to https redirections")
    followHttpToHttpsRedirect: Boolean = true,

  @Help("Credentials to be used when fetching metadata or artifacts. Specify multiple times to pass multiple credentials. Alternatively, use the COURSIER_CREDENTIALS environment variable")
  @Value("host(realm) user:pass|host user:pass")
    credentials: List[String] = Nil,

  @Help("Path to credential files to read credentials from")
    credentialFile: List[String] = Nil,

  @Help("Whether to read credentials from COURSIER_CREDENTIALS (env) or coursier.credentials (Java property), along those passed with --credentials and --credential-file")
    useEnvCredentials: Boolean = true

) {

  def params: ValidatedNel[String, CacheParams] =
    params(CacheDefaults.ttl)

  def params(defaultTtl: Option[Duration]): ValidatedNel[String, CacheParams] = {

    val cache0 = cache match {
      case Some(path) => new File(path)
      case None => CacheDefaults.location
    }

    val cachePoliciesV =
      if (mode.isEmpty)
        Validated.validNel(CacheDefaults.cachePolicies)
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
        Validated.validNel(defaultTtl)
      else
        CacheDefaults.parseDuration(ttl) match {
          case Left(e) => Validated.invalidNel(s"Parsing TTL: ${e.getMessage}")
          case Right(d) => Validated.validNel(Some(d))
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

    // FIXME Here, we're giving direct credentials a higher priority than file credentials,
    //       even if some of the latter were passed before the former on the command-line

    val credentialsV = credentials.traverse { s =>
      CredentialsParser.parseSeq(s).either match {
        case Left(errors) =>
          Validated.invalid(NonEmptyList.of(errors.head, errors.tail: _*))
        case Right(l) =>
          Validated.validNel(l)
      }
    }.map(_.flatten)

    val credentialFiles = credentialFile.map { f =>
      // warn if f doesn't exist or has too open permissions?
      FileCredentials(f)
    }

    (cachePoliciesV, ttlV, parallelV, checksumV, retryCountV, credentialsV).mapN {
      (cachePolicy, ttl, parallel, checksum, retryCount, credentials0) =>
        CacheParams(cache0, cachePolicy, ttl, parallel, checksum, retryCount, cacheFileArtifacts, followHttpToHttpsRedirect)
          .withCredentials(credentials0 ++ credentialFiles)
          .withUseEnvCredentials(useEnvCredentials)
    }
  }
}

object CacheOptions {
  implicit val parser = Parser[CacheOptions]
  implicit val help = caseapp.core.help.Help[CacheOptions]
}
