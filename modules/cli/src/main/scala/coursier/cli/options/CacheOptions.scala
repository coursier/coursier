package coursier.cli.options

import java.io.File

import caseapp._
import cats.data.{NonEmptyList, Validated, ValidatedNel}
import cats.implicits._
import coursier.cache.CacheDefaults
import coursier.cli.params.CacheParams
import coursier.credentials.FileCredentials
import coursier.parse.{CachePolicyParser, CredentialsParser}

import scala.concurrent.duration.Duration

// format: off
final case class CacheOptions(

  @Group(OptionGroup.cache)
  @HelpMessage("Cache directory (defaults to environment variable COURSIER_CACHE, or ~/.cache/coursier/v1 on Linux and ~/Library/Caches/Coursier/v1 on Mac)")
    cache: Option[String] = None,

  @Group(OptionGroup.cache)
  @Hidden
  @HelpMessage("Download mode (default: missing, that is fetch things missing from cache)")
  @ValueDescription("offline|update-changing|update|missing|force")
  @ExtraName("m")
    mode: String = "",

  @Group(OptionGroup.cache)
  @HelpMessage("TTL duration (e.g. \"24 hours\")")
  @ValueDescription("duration")
  @ExtraName("l")
    ttl: Option[String] = None,

  @Group(OptionGroup.cache)
  @Hidden
  @HelpMessage("Maximum number of parallel downloads (default: 6)")
  @ExtraName("n")
    parallel: Int = 6,

  @Group(OptionGroup.cache)
  @Hidden
  @HelpMessage("Checksum types to check - end with none to allow for no checksum validation if no checksum is available, example: SHA-256,SHA-1,none")
  @ValueDescription("checksum1,checksum2,...")
    checksum: List[String] = Nil,

  @Group(OptionGroup.cache)
  @Hidden
  @HelpMessage("Retry limit for Checksum error when fetching a file")
    retryCount: Int = 1,

  @Group(OptionGroup.cache)
  @Hidden
  @HelpMessage("Flag that specifies if a local artifact should be cached.")
  @ExtraName("cfa")
    cacheFileArtifacts: Boolean = false,

  @Group(OptionGroup.cache)
  @Hidden
  @HelpMessage("Whether to follow http to https redirections")
    followHttpToHttpsRedirect: Boolean = true,

  @Group(OptionGroup.cache)
  @HelpMessage("Credentials to be used when fetching metadata or artifacts. Specify multiple times to pass multiple credentials. Alternatively, use the COURSIER_CREDENTIALS environment variable")
  @ValueDescription("host(realm) user:pass|host user:pass")
    credentials: List[String] = Nil,

  @Group(OptionGroup.cache)
  @HelpMessage("Path to credential files to read credentials from")
    credentialFile: List[String] = Nil,

  @Group(OptionGroup.cache)
  @Hidden
  @HelpMessage("Whether to read credentials from COURSIER_CREDENTIALS (env) or coursier.credentials (Java property), along those passed with --credentials and --credential-file")
    useEnvCredentials: Boolean = true

) {
  // format: on

  def params: ValidatedNel[String, CacheParams] =
    params(CacheDefaults.ttl)

  def params(defaultTtl: Option[Duration]): ValidatedNel[String, CacheParams] = {

    val cache0 = cache match {
      case Some(path) => new File(path)
      case None       => CacheDefaults.location
    }

    val cachePoliciesV =
      if (mode.isEmpty)
        Validated.validNel(CacheDefaults.cachePolicies)
      else
        CachePolicyParser.cachePolicies(mode, CacheDefaults.cachePolicies).either match {
          case Right(cp) =>
            Validated.validNel(cp)
          case Left(errors) =>
            Validated.invalidNel(
              s"Error parsing modes:" +
                System.lineSeparator() +
                errors.map("  " + _).mkString(System.lineSeparator())
            )
        }

    val ttlV = {
      val ttlOpt = ttl.map(_.trim).filter(_.nonEmpty)
      ttlOpt match {
        case None =>
          Validated.validNel(defaultTtl)
        case Some(ttlStr) =>
          CacheDefaults.parseDuration(ttlStr) match {
            case Left(e)  => Validated.invalidNel(s"Parsing TTL: ${e.getMessage}")
            case Right(d) => Validated.validNel(Some(d))
          }
      }
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
            case sumType                            => Some(sumType)
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
        val baseParams = CacheParams(
          cache0,
          cachePolicy,
          ttl,
          parallel,
          checksum,
          retryCount,
          cacheFileArtifacts,
          followHttpToHttpsRedirect
        )
        baseParams
          .withCredentials(credentials0 ++ credentialFiles)
          .withUseEnvCredentials(useEnvCredentials)
    }
  }
}

object CacheOptions {
  lazy val parser: Parser[CacheOptions]                           = Parser.derive
  implicit lazy val parserAux: Parser.Aux[CacheOptions, parser.D] = parser
  implicit lazy val help: Help[CacheOptions]                      = Help.derive
}
