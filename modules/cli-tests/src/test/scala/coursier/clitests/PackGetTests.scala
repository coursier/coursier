package coursier.clitests

object PackGetTests extends GetTests {
  val launcher       = LauncherTestUtil.launcher
  def isNative       = LauncherTestUtil.isNative
  def isNativeStatic = LauncherTestUtil.isNativeStatic
}
