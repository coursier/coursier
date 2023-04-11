package coursier.clitests

import java.util.Locale

import scala.util.Properties

trait PackLauncherOptions extends LauncherOptions {
  override lazy val acceptsDOptions =
    sys.props.get("coursier-test-launcher-accepts-D").map(_.toLowerCase(Locale.ROOT)) match {
      case Some("true")  => true
      case Some("false") => false
      case None          => true
      case Some(other) =>
        System.err.println(s"Warning: unrecognized coursier-test-launcher-accepts-D value '$other'")
        true
    }
  override lazy val acceptsJOptions =
    sys.props.get("coursier-test-launcher-accepts-J").map(_.toLowerCase(Locale.ROOT)) match {
      case Some("true")  => true
      case Some("false") => false
      case None          => !Properties.isWin
      case Some(other) =>
        System.err.println(s"Warning: unrecognized coursier-test-launcher-accepts-J value '$other'")
        true
    }
}
