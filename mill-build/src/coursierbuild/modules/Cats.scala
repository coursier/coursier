package coursierbuild.modules

import coursierbuild.Deps

import mill._, mill.scalalib._

trait Cats extends CsModule with SnapshotOnlyPublishModule {
  def artifactName = "coursier-cats-interop"
  def mvnDeps      = super.mvnDeps() ++ Seq(
    Deps.catsEffect
  )
}
