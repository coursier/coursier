package coursier

import coursier.util.Repositories

abstract class PlatformResolve {

  val defaultRepositories: Seq[Repository] =
    Seq(
      Repositories.central
    )

}
