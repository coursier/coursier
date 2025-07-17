package coursierbuild.modules

import mill._

trait SharedScalaSources extends Module {
  def sources = Task.Sources(
    os.sub / "src/main/scala",
    os.sub / "src/main/java"
  )
  def testSources = Task.Sources(
    os.sub / "src/test/scala",
    os.sub / "src/test/java"
  )
}
