package coursier.cli.get

import cats.data.{Validated, ValidatedNel}
import cats.implicits._
import coursier.cache.CacheDefaults
import coursier.cli.params.{CacheParams, OutputParams}

import java.io.File

final case class GetParams(
  cache: CacheParams,
  output: OutputParams,
  separator: String,
  force: Boolean,
  changing: Option[Boolean],
  archiveOpt: Option[Boolean],
  archiveCacheLocation: File,
  authHeaders: Seq[(String, String)]
)

object GetParams {
  def apply(options: GetOptions): ValidatedNel[String, GetParams] = {
    val cacheV  = options.cache.params
    val outputV = OutputParams(options.output)

    val separatorV = (options.zero, options.separator) match {
      case (false, None)          => Validated.validNel(System.lineSeparator)
      case (false, Some(sep))     => Validated.validNel(sep)
      case (true, None)           => Validated.validNel("\u0000")
      case (true, Some("\u0000")) => Validated.validNel("\u0000")
      case (true, Some(_))        =>
        Validated.invalidNel("--zero and --separator cannot be specific at the same time")
    }

    val authHeadersV = options.authHeader
      .filter(_.trim.nonEmpty)
      .traverse { input =>
        input.split(":\\s*", 2) match {
          case Array(k, v) => Validated.valid(k.trim -> v)
          case Array(_)    =>
            Validated.invalidNel(s"Malformed auth header value: '$input', expected 'header: value'")
        }
      }

    (cacheV, outputV, separatorV, authHeadersV).mapN {
      case (cache, output, separator, authHeaders) =>
        GetParams(
          cache,
          output,
          separator,
          options.force,
          options.changing,
          options.archive,
          options.archiveCache.map(new File(_)).getOrElse {
            CacheDefaults.archiveCacheLocation
          },
          authHeaders
        )
    }
  }
}
