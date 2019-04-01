package coursier

abstract class PlatformResolve {

  val defaultRepositories: Seq[Repository] =
    Seq(
      Repositories.central
    )

}
