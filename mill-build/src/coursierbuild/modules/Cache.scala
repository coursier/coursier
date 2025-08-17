package coursierbuild.modules

import coursierbuild.Deps.Deps
import mill._
import com.github.lolgab.mill.mima._

trait Cache extends CsModule with CsCrossJvmJsModule with CoursierPublishModule {
  def artifactName = "coursier-cache"
  def compileIvyDeps = Agg(
    Deps.dataClass
  )
}
