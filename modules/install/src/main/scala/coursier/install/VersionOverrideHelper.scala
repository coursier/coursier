package coursier.install

import scala.annotation.nowarn

// put in a separate file to work around https://github.com/scala/bug/issues/12498
object VersionOverrideHelper {
  @nowarn
  type LegacyVersionInterval = coursier.core.VersionInterval
}
