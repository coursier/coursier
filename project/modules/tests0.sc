import $file.^.deps, deps.Deps
import $file.shared, shared.CsCrossJvmJsModule
import mill._

trait TestsModule extends CsCrossJvmJsModule {
  def ivyDeps = Agg(
    Deps.collectionCompat,
    Deps.scalaAsync
  )
  def compileIvyDeps = Agg(
    Deps.dataClass,
    Deps.simulacrum
  )
}
