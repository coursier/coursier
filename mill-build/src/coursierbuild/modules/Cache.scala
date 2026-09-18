package coursierbuild.modules

import coursierbuild.Deps
import mill._
import com.github.lolgab.mill.mima._

trait Cache extends CsModule with CoursierPublishModule {
  def artifactName = "coursier-cache"
}
