package coursier.error

import coursier.core.Module
import coursier.version.VersionConstraint

/** The chain of parent POMs of a module loops back on itself
  *
  * @param chain
  *   the module and its parents, the last one being already present earlier in the chain
  */
final class ParentCycleError(val chain: Seq[(Module, VersionConstraint)])
    extends DependencyError(
      "Cycle in parent POMs: " +
        chain.map { case (mod, ver) => s"${mod.repr}:${ver.asString}" }.mkString(" -> ")
    )
