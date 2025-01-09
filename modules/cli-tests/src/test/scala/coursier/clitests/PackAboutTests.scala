package coursier.clitests

object PackAboutTests extends AboutTests with PackLauncherOptions {
  val launcher = LauncherTestUtil.launcher
  def isNative = LauncherTestUtil.isNative
}
