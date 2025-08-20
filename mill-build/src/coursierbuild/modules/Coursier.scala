package coursierbuild.modules

import coursierbuild.Deps.Deps

import mill._, mill.scalalib._

trait Coursier extends CsModule with CsCrossJvmJsModule with CoursierPublishModule {
  def artifactName = "coursier"
  def compileIvyDeps = super.compileIvyDeps() ++ Agg(
    Deps.dataClass,
    Deps.jsoniterMacros,
    Deps.scalaReflect(scalaVersion()) // ???
  )
  def ivyDeps = super.ivyDeps() ++ Agg(
    Deps.dependency,
    Deps.fastParse,
    Deps.jsoniterCore
  )
}
