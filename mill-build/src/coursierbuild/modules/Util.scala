package coursierbuild.modules

import coursierbuild.Deps.Deps
import coursierbuild.Shading
import mill._
import com.github.lolgab.mill.mima._

trait Util extends CsModule with CsCrossJvmJsModule with CoursierPublishModule {
  def artifactName = "coursier-util"
  def ivyDeps = Agg(
    Deps.collectionCompat
  )
  def compileIvyDeps = Agg(
    Deps.dataClass
  )
}
