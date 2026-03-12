package coursierbuild.modules

import coursierbuild.Deps.Deps
import mill._

trait Util extends CsModule with CsCrossJvmJsModule with CoursierPublishModule {
  def artifactName = "coursier-util"
  def mvnDeps = Seq(
    Deps.collectionCompat
  )
  def compileMvnDeps = Seq(
    Deps.dataClass
  )
}
