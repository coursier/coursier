package coursier.util

import coursier.core._
import coursier.ivy.IvyRepository
import coursier.maven.MavenRepository

/** Scala 3 counterpart of the Scala 2 macro-based interpolators.
  *
  * Like the Scala 2 ones, these validate their literal at compile-time, and expand to the value it
  * was parsed to. The macro implementations live in [[StringInterpolatorsMacros]].
  */
object StringInterpolators {

  extension (inline sc: StringContext) {

    inline def org(inline args: Any*): Organization =
      ${ StringInterpolatorsMacros.orgImpl('sc) }

    inline def name(inline args: Any*): ModuleName =
      ${ StringInterpolatorsMacros.nameImpl('sc) }

    inline def mod(inline args: Any*): Module =
      ${ StringInterpolatorsMacros.modImpl('sc) }

    inline def excl(inline args: Any*): ModuleMatchers =
      ${ StringInterpolatorsMacros.exclImpl('sc) }

    inline def incl(inline args: Any*): ModuleMatchers =
      ${ StringInterpolatorsMacros.inclImpl('sc) }

    inline def dep(inline args: Any*): Dependency =
      ${ StringInterpolatorsMacros.depImpl('sc) }

    inline def mvn(inline args: Any*): MavenRepository =
      ${ StringInterpolatorsMacros.mvnImpl('sc) }

    inline def ivy(inline args: Any*): IvyRepository =
      ${ StringInterpolatorsMacros.ivyImpl('sc) }
  }
}
