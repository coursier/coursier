package coursierbuild.modules

import coursierbuild.Deps.Deps

import mill._, mill.scalalib._

trait Scalaz extends CsModule with CsCrossJvmJsModule with SnapshotOnlyPublishModule {
  def artifactName = "coursier-scalaz-interop"
}
