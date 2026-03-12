package coursier.clitests

import scala.util.Properties

object PackBootstrapTests extends BootstrapTests with PackLauncherOptions {
  val launcher = LauncherTestUtil.launcher
  val assembly = LauncherTestUtil.assembly
  override lazy val overrideProguarded: Option[Boolean] =
    if (sys.props.get("java.version").exists(!_.startsWith("1.")))
      // It seems bootstrap JARs built on Java 11 fail at runtime with some obscure
      // java.lang.VerifyError: Bad type on operand stack
      Some(false)
    else
      None
  override lazy val enableNailgunTest: Boolean =
    // TODO Re-enable that on Windows using snailgun?
    !Properties.isWin &&
    // running into weird class loading errors with nailgun and JDK >= 11
    sys.props.get("java.version").exists(_.startsWith("1.")) &&
    // Disabling nailgun mac test on CI (seems we can't install nailgun with brew anymore on GitHub Mac runners)
    (!Properties.isMac || System.getenv("CI") == null)
}
