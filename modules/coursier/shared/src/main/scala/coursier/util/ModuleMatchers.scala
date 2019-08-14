package coursier.util

import coursier.core.{Module, ModuleName, Organization}

final case class ModuleMatchers(
  exclude: Set[ModuleMatcher],
  include: Set[ModuleMatcher] = Set(),
  includeByDefault: Boolean = true
) {

  // If modules are included by default:
  // Those matched by anything in exclude are excluded, but for those also matched by something in include.
  // If modules are excluded by default:
  // Those matched by anything in include are included, but for those also matched by something in exclude.

  def matches(module: Module): Boolean =
    if (includeByDefault)
      !exclude.exists(_.matches(module)) || include.exists(_.matches(module))
    else
      include.exists(_.matches(module)) && !exclude.exists(_.matches(module))

  def +(other: ModuleMatchers): ModuleMatchers =
    ModuleMatchers(
      exclude ++ other.exclude,
      include ++ other.include
    )

}

object ModuleMatchers {

  def all: ModuleMatchers =
    ModuleMatchers(Set.empty, Set.empty)

  def only(mod: Module): ModuleMatchers =
    ModuleMatchers(Set.empty, Set(ModuleMatcher(mod)), includeByDefault = false)
  def only(mod: ModuleMatcher): ModuleMatchers =
    ModuleMatchers(Set.empty, Set(mod), includeByDefault = false)
  def only(org: Organization, name: ModuleName): ModuleMatchers =
    ModuleMatchers(Set.empty, Set(ModuleMatcher(Module(org, name, Map.empty))), includeByDefault = false)

}
