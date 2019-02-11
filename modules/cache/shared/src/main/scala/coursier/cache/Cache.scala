package coursier.cache

import coursier.core.{Artifact, Repository}

abstract class Cache[F[_]] extends PlatformCache[F] {

  /**
    * Method to fetch an [[Artifact]].
    *
    * Note that this method tries all the [[coursier.CachePolicy]]ies of this cache straightaway. During resolutions, you should
    * prefer to try all repositories for the first policy, then the other policies if needed (in pseudo-code,
    * `for (policy <- policies; repo <- repositories) …`, rather than
    * `for (repo <- repositories, policy <- policies) …`). You should use the [[fetchs]] method in that case.
    */
  def fetch: Repository.Fetch[F]

  /**
    * Sequence of [[Repository.Fetch]] able to fetch an [[Artifact]].
    *
    * Each element correspond to a [[coursier.CachePolicy]] of this [[Cache]]. You may want to pass each of them to
    * [[coursier.core.ResolutionProcess.fetch()]].
    *
    * @return a non empty sequence
    */
  def fetchs: Seq[Repository.Fetch[F]]
}

object Cache extends PlatformCacheCompanion
