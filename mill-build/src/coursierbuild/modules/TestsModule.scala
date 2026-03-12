package coursierbuild.modules

import coursierbuild.Deps.Deps
import mill._

trait TestsModule extends CsCrossJvmJsModule {
  def mvnDeps = Seq(
    Deps.collectionCompat,
    Deps.pprint,
    Deps.scalaAsync
  )
  def compileMvnDeps = Seq(
    Deps.dataClass
  )
}
