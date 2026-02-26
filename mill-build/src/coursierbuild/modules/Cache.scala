package coursierbuild.modules

import com.github.lolgab.mill.mima.*
import coursierbuild.Deps.Deps
import mill.*

trait Cache extends CsModule with CsCrossJvmJsModule with CoursierPublishModule {
  def artifactName = "coursier-cache"
  def compileMvnDeps = Seq(
    Deps.dataClass
  )
}
