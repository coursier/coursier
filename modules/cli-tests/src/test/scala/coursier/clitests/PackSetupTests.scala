package coursier.clitests

object PackSetupTests extends SetupTests {
  val launcher       = LauncherTestUtil.launcher
  val assembly       = LauncherTestUtil.assembly
  def isNative       = LauncherTestUtil.isNative
  def isNativeStatic = LauncherTestUtil.isNativeStatic
}
