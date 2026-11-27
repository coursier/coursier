package coursier.cache

import java.nio.file.Path

import coursier.credentials.{Credentials, DirectCredentials, Password}

import scala.cli.config.{ConfigDb, Keys}

/** The part of [[CacheEnv]] that reads Scala CLI's configuration file - its config library is
  * JVM-only.
  */
abstract class PlatformCacheEnv {

  def credentialsFromConfig(configPath: Path): Seq[Credentials] = {
    val configDb = ConfigDb.open(configPath).fold(e => throw new Exception(e), identity)
    configDb.get(Keys.repositoryCredentials).fold(
      e => throw new Exception(e),
      _.getOrElse(Nil).map { c =>
        DirectCredentials(
          c.host,
          c.user.map(_.get().value),
          c.password.map(p => Password(p.get().value)),
          c.realm,
          c.optional.getOrElse(DirectCredentials.defaultOptional),
          c.matchHost.getOrElse(DirectCredentials.defaultMatchHost),
          // not sure about the default value here
          c.httpsOnly.getOrElse(DirectCredentials.defaultHttpsOnly),
          // not sure either about this one
          c.passOnRedirect.getOrElse(DirectCredentials.defaultPassOnRedirect)
        )
      }
    )
  }

}
