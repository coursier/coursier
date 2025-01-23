package coursier

import scala.annotation.nowarn

private[coursier] object ResolveInternals {
  // running into https://github.com/scala/bug/issues/12498 when
  // putting that method in Resolve.scala, so it lives in an
  // external file and class here
  @nowarn
  def deprecatedBomDependencies[F[_]](resolve: Resolve[F]) =
    resolve.bomDependencies
  @nowarn
  def deprecatedMapDependenciesOpt[F[_]](resolve: Resolve[F]) =
    resolve.mapDependenciesOpt
  @nowarn
  def deprecatedBomModuleVersions[F[_]](resolve: Resolve[F]) =
    resolve.bomModuleVersions
}
