package coursier.core

import scala.annotation.nowarn

private[core] object DependencyInternals {
  // running into https://github.com/scala/bug/issues/12498 when
  // putting that method in Dependency.scala, so it lives in an
  // external file and class here
  @nowarn
  def deprecatedBoms(dep: Dependency) =
    dep.boms
}
