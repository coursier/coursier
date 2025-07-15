package coursierbuild.modules

import coursierbuild.Deps.Deps
import mill._

trait TestsModule extends CsCrossJvmJsModule {
  def ivyDeps = Agg(
    Deps.collectionCompat,
    Deps.pprint,
    Deps.scalaAsync
  )
  def compileIvyDeps = Agg(
    Deps.dataClass
  )
}
