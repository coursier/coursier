package coursier.cache

import java.nio.file.Path

import coursier.credentials.Credentials

/** Scala Native counterpart of the JVM [[PlatformCacheEnv]].
  *
  * Scala CLI's configuration library is JVM-only, so credentials can only come from coursier's own
  * `credentials.properties` files and from `COURSIER_CREDENTIALS` here.
  */
abstract class PlatformCacheEnv {

  def credentialsFromConfig(configPath: Path): Seq[Credentials] = Nil

}
