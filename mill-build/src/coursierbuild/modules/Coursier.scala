package coursierbuild.modules

import coursierbuild.Deps.Deps

import mill._, mill.scalalib._

trait Coursier extends CsModule with CsCrossJvmJsModule with CoursierPublishModule {
  def artifactName = "coursier"
  def compileMvnDeps = Task {
    val sv = scalaVersion()
    // scala-reflect only exists for Scala 2
    val scala2Extra = if (sv.startsWith("2.")) Seq(Deps.scalaReflect(sv)) else Nil
    super.compileMvnDeps() ++ Seq(
      Deps.jsoniterMacros
    ) ++ scala2Extra
  }
  def mvnDeps = super.mvnDeps() ++ Seq(
    Deps.dependency,
    Deps.fastParse,
    Deps.jsoniterCore
  )
}
