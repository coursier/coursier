package coursier.cache

import scala.cli.config.Secret

/** The part of [[CacheDefaults]] that leans on Scala CLI's config types, which are JVM-only. */
abstract class PlatformCacheDefaults {

  lazy val cacheServerBasicAuth: Option[Secret[String]] = {
    val userValues     = CacheEnv.serverUser.read()
    val passwordValues = CacheEnv.serverPassword.read()
    val userOpt        = userValues.prop.orElse(userValues.env)
    val passwordOpt    = passwordValues.prop.orElse(passwordValues.env)
    if (userOpt.isEmpty && passwordOpt.isEmpty)
      None
    else
      Some(Secret((userOpt.toSeq ++ passwordOpt.toSeq).mkString(":")))
  }

}
