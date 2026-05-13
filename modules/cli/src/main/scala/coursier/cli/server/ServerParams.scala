package coursier.cli.server

import cats.data.ValidatedNel
import cats.implicits._
import coursier.cli.params.{CacheParams, OutputParams}
import scala.cli.config.Secret

final case class ServerParams(
  cache: CacheParams,
  output: OutputParams,
  host: String,
  port: Int,
  userPassword: Option[(Secret[String], Secret[String])],
  defaultAllow: Boolean,
  allow: List[String],
  disallow: List[String]
)

object ServerParams {
  def apply(options: ServerOptions): ValidatedNel[String, ServerParams] = {
    val cacheV  = options.cache.params
    val outputV = OutputParams(options.output)

    val userPasswordV: ValidatedNel[String, Option[(Secret[String], Secret[String])]] =
      if (options.noAuth) None.validNel
      else
        (options.user, options.password) match {
          case (Some(user), Some(password)) =>
            Some((user.get(), password.get())).validNel
          case (Some(_), None) =>
            "Missing --password option".invalidNel
          case (None, Some(_)) =>
            "Missing --user option".invalidNel
          case (None, None) =>
            "Missing --user and --password options".invalidNel
        }

    val defaultAllowV: ValidatedNel[String, Boolean] =
      options.defaultAllow match {
        case Some(true) =>
          options.defaultDisallow match {
            case Some(true) =>
              "--default-allow and --default-disallow cannot be specified at the same time".invalidNel
            case _ =>
              true.validNel
          }
        case Some(false) =>
          options.defaultDisallow match {
            case Some(false) =>
              "--default-allow and --default-disallow cannot be disabled at the same time".invalidNel
            case _ =>
              false.validNel
          }
        case None =>
          options.defaultDisallow match {
            case Some(true) =>
              false.validNel
            case _ =>
              true.validNel
          }
      }

    (cacheV, outputV, userPasswordV, defaultAllowV).mapN {
      (cache, output, userPassword, defaultAllow) =>
        ServerParams(
          cache = cache,
          output = output,
          host = options.host,
          port = options.port,
          userPassword = userPassword,
          defaultAllow = defaultAllow,
          allow = options.allow.flatMap(_.split(",")).map(_.trim).filter(_.nonEmpty),
          disallow = options.disallow.flatMap(_.split(",")).map(_.trim).filter(_.nonEmpty)
        )
    }
  }
}
