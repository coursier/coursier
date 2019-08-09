package coursier.cache

import coursier.util.{Artifact, EitherT}

import scala.concurrent.ExecutionContext

abstract class Cache[F[_]] extends PlatformCache[F] {

  import Cache.Fetch

  /**
    * Method to fetch an [[Artifact]].
    *
    * Note that this method tries all the [[coursier.cache.CachePolicy]]ies of this cache straightaway. During resolutions, you should
    * prefer to try all repositories for the first policy, then the other policies if needed (in pseudo-code,
    * `for (policy <- policies; repo <- repositories) …`, rather than
    * `for (repo <- repositories, policy <- policies) …`). You should use the [[fetchs]] method in that case.
    */
  def fetch: Fetch[F]

  /**
    * Sequence of [[Fetch]] able to fetch an [[Artifact]].
    *
    * Each element correspond to a [[coursier.cache.CachePolicy]] of this [[Cache]]. You may want to pass each of them to
    * `coursier.core.ResolutionProcess.fetch()`.
    *
    * @return a non empty sequence
    */
  def fetchs: Seq[Fetch[F]] =
    Seq(fetch)

  def ec: ExecutionContext

  def loggerOpt: Option[CacheLogger] =
    None
}

object Cache extends PlatformCacheCompanion {

  type Fetch[F[_]] = Artifact => EitherT[F, String, String]

}
