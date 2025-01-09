package coursier.clitests

object PackAboutTests extends AboutTests with PackLauncherOptions {
  val launcher       = LauncherTestUtil.launcher
  val assembly       = LauncherTestUtil.assembly
  def isNative       = LauncherTestUtil.isNative
  def isNativeStatic = LauncherTestUtil.isNativeStatic
}
