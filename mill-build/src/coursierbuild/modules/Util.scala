package coursierbuild.modules

import coursierbuild.Deps.Deps
import coursierbuild.Shading
import mill._
import com.github.lolgab.mill.mima._

trait Util extends CsModule with CsCrossJvmJsModule with CoursierPublishModule {
  def artifactName = "coursier-util"
  def mvnDeps = Seq(
    Deps.collectionCompat
  )
  def compileMvnDeps = Seq(
    Deps.dataClass
  )
}
