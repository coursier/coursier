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
  override lazy val acceptsJOptions =
    sys.props.get("coursier-test-launcher-accepts-J").map(_.toLowerCase(Locale.ROOT)) match {
      case Some("true")  => true
      case Some("false") => false
      case None          => !LauncherTestUtil.isWindows
      case Some(other)   =>
        System.err.println(s"Warning: unrecognized coursier-test-launcher-accepts-J value '$other'")
        true
    }
  override lazy val overrideProguarded: Option[Boolean] =
    if (sys.props.get("java.version").exists(!_.startsWith("1.")))
      // It seems bootstrap JARs built on Java 11 fail at runtime with some obscure
      // java.lang.VerifyError: Bad type on operand stack
      Some(false)
    else
      None
  override lazy val enableNailgunTest: Boolean =
    // running into weird class loading errors with nailgun and JDK >= 11
    sys.props.get("java.version").exists(_.startsWith("1."))
}
