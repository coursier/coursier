package coursier.core

import scala.annotation.nowarn

private[core] object ResolutionInternals {
  // running into https://github.com/scala/bug/issues/12498 when
  // putting that method in Resolution.scala, so it lives in an
  // external file and class here
  @nowarn
  def deprecatedBomDependencies(res: Resolution) =
    res.bomDependencies
  @nowarn
  def deprecatedBomModuleVersions(res: Resolution) =
    res.bomModuleVersions
}
