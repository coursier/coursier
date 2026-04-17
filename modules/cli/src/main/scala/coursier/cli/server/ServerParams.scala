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
  userPassword: Option[(Secret[String], Secret[String])]
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

    (cacheV, outputV, userPasswordV).mapN { (cache, output, userPassword) =>
      ServerParams(
        cache = cache,
        output = output,
        host = options.host,
        port = options.port,
        userPassword = userPassword
      )
    }
  }
}
