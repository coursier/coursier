package coursierbuild.modules

import coursierbuild.Deps.Deps
import mill._, mill.scalalib._

trait CsScalaModule extends ScalaModule with CoursierJavaModule {
  def scalacOptions = Task {
    val sv           = scalaVersion()
    val scala212Opts =
      if (sv.startsWith("2.12.")) Seq("-Ypartial-unification", "-language:higherKinds")
      else Nil
    val scala213Opts =
      if (sv.startsWith("2.13.")) Seq("-Ymacro-annotations", "-Wunused:nowarn", "-Ytasty-reader")
      else Nil
    val scala2Opts =
      if (sv.startsWith("2.")) Seq("-Xasync")
      else Nil
    super.scalacOptions() ++ scala212Opts ++ scala213Opts ++ scala2Opts ++ Seq(
      "-deprecation",
      "-feature",
      "--release",
      jvmRelease
    )
  }
  def scalacPluginIvyDeps = Task {
    val sv              = scalaVersion()
    val scala212Plugins =
      if (sv.startsWith("2.12.")) Agg(Deps.macroParadise)
      else Nil
    super.scalacPluginIvyDeps() ++ scala212Plugins
  }
}
