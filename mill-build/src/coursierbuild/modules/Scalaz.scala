package coursierbuild.modules

import coursierbuild.Deps.Deps
import mill.*
import mill.scalalib.*

trait Scalaz extends CsModule with CsCrossJvmJsModule with CoursierPublishModule {
  def artifactName = "coursier-scalaz-interop"
}
