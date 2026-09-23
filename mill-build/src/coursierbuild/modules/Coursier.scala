package coursierbuild.modules

import coursierbuild.Deps

import mill._, mill.scalalib._

trait Coursier extends CsModule with CoursierPublishModule {
  def artifactName = "coursier"
  def compileMvnDeps = Task {
    val sv          = scalaVersion()
    val scala2Extra = if (sv.startsWith("2.")) Seq(Deps.scalaReflect(sv)) else Nil
    super.compileMvnDeps() ++ Seq(Deps.jsoniterMacros(sv)) ++ scala2Extra
  }
  def mvnDeps = super.mvnDeps() ++ Seq(
    Deps.dependency,
    Deps.fastParse,
    Deps.jsoniterCore(scalaVersion())
  )
}
