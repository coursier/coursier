package coursier

import coursier.cache.Cache
import coursier.params.MirrorConfFile

abstract class PlatformResolve {

  protected def defaultMirrorConfFiles: Seq[MirrorConfFile] =
    Nil

  val defaultRepositories: Seq[Repository] =
    Seq(
      Repositories.central
    )

  def defaultListVersionCache[F[_]](cache: Cache[F]): Cache[F] =
    cache

}
