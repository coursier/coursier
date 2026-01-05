package coursierbuild.modules

import coursierbuild.Deps.Deps
import mill._

trait SbtMavenRepository extends CsModule with CsCrossJvmJsModule with CoursierPublishModule {
  def artifactName = "coursier-sbt-maven-repository"
  def compileMvnDeps = super.compileMvnDeps() ++ Seq(
    Deps.dataClass
  )
}
