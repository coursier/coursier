package coursier.cache

/** Scala Native counterpart of the JVM [[PlatformCacheDefaults]].
  *
  * `cacheServerBasicAuth` is JVM-only - it is typed with Scala CLI's `Secret`, and only
  * [[RemoteCache]] (also JVM-only) reads it.
  */
abstract class PlatformCacheDefaults
