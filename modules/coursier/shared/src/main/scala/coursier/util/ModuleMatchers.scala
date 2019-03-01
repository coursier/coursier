package coursier.util

import coursier.core.Module

final case class ModuleMatchers(
  exclude: Set[ModuleMatcher],
  include: Set[ModuleMatcher] = Set()
) {

  // Modules are included by default.
  // Those matched by anything in exclude are excluded, but for those also matched by something in include.

  // Maybe an extra parameter could be added to change the default (include).

  def matches(module: Module): Boolean =
    !exclude.exists(_.matches(module)) || include.exists(_.matches(module))

  def +(other: ModuleMatchers): ModuleMatchers =
    ModuleMatchers(
      exclude ++ other.exclude,
      include ++ other.include
    )

}

object ModuleMatchers {

  def all: ModuleMatchers =
    ModuleMatchers(Set.empty, Set.empty)

}
