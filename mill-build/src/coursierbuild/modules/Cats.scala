package coursierbuild.modules

import coursierbuild.Deps.Deps

import mill._, mill.scalalib._

trait Cats extends CsModule with CsCrossJvmJsModule with CoursierPublishModule {
  def artifactName = "coursier-cats-interop"
  def ivyDeps      = super.ivyDeps() ++ Agg(
    Deps.catsEffect
  )
}
