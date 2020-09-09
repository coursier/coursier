package coursier.clitests

import java.util.Locale

object PackBootstrapTests extends BootstrapTests {
  val launcher = LauncherTestUtil.launcher
  override lazy val acceptsDOptions =
    sys.props.get("coursier-test-launcher-accepts-D").map(_.toLowerCase(Locale.ROOT)) match {
      case Some("true")  => true
      case Some("false") => false
      case None          => true
      case Some(other)   =>
        System.err.println(s"Warning: unrecognized coursier-test-launcher-accepts-D value '$other'")
        true
    }
}
