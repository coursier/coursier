package coursier.core

import scala.annotation.nowarn

private[core] object VersionInternals {
  // Only Scala 3 warns about the deprecated Version class here. Scala 2 doesn't,
  // and would warn about an unused @nowarn instead, hence the per-version files.
  @nowarn
  val zero = Version("0")
}
