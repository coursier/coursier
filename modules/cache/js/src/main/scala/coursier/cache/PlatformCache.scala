package coursier.cache

abstract class PlatformCache[F[_]] {
  def loggerOpt: Option[CacheLogger] =
    None
}
