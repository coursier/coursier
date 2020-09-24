package coursier.clitests

object PackInstallTests extends InstallTests {
  val launcher = LauncherTestUtil.launcher
  override lazy val overrideProguarded: Option[Boolean] =
    if (sys.props.get("java.version").exists(!_.startsWith("1.")))
      // It seems bootstrap JARs built on Java 11 fail at runtime with some obscure
      // java.lang.VerifyError: Bad type on operand stack
      Some(false)
    else
      None
}
