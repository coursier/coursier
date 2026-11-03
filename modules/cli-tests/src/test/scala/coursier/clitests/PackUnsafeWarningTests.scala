package coursier.clitests

object PackUnsafeWarningTests extends UnsafeWarningTests {
  val launcher = LauncherTestUtil.launcher
  def isNative = LauncherTestUtil.isNative
}
