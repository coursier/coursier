package coursierbuild.modules

import coursierbuild.Deps
import mill._

trait TestsModule extends CsModule {
  def mvnDeps = Seq(
    Deps.collectionCompat,
    Deps.pprint
  )
  def compileMvnDeps = Seq(
    Deps.dataClass
  )
}
