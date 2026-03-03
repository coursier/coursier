package coursierbuild.modules

import coursierbuild.Deps.Deps
import mill.*
import mill.scalalib.*

trait Cats extends CsModule with CsCrossJvmJsModule with CoursierPublishModule {
  def artifactName = "coursier-cats-interop"
  def mvnDeps = super.mvnDeps() ++ Seq(
    Deps.catsEffect
  )
}
