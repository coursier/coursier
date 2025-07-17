package coursierbuild.modules

import coursierbuild.Deps.Deps
import coursierbuild.Shading
import com.github.lolgab.mill.mima._

import mill._, mill.scalalib._

trait Coursier extends CsModule with CsCrossJvmJsModule with CoursierPublishModule {
  def artifactName = "coursier"
  def compileMvnDeps = super.compileMvnDeps() ++ Seq(
    Deps.dataClass,
    Deps.jsoniterMacros,
    Deps.scalaReflect(scalaVersion()) // ???
  )
  def mvnDeps = super.mvnDeps() ++ Seq(
    Deps.dependency,
    Deps.fastParse,
    Deps.jsoniterCore
  )
}
