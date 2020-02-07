package coursier.launcher.internal

import java.util.Locale

object Windows {

  def isWindows: Boolean =
    sys.props
      .get("os.name")
      .map(_.toLowerCase(Locale.ROOT))
      .exists(_.contains("windows"))

}
