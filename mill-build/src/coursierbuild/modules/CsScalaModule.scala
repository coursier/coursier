package coursierbuild.modules

import coursierbuild.Deps.Deps
import mill._, mill.scalalib._

trait CsScalaModule extends ScalaModule with CoursierJavaModule {
  def scalacOptions = Task {
    val sv = scalaVersion()
    val scala212Opts =
      if (sv.startsWith("2.12.")) Seq("-Ypartial-unification", "-language:higherKinds")
      else Nil
    val scala213Opts =
      if (sv.startsWith("2.13.")) Seq("-Ymacro-annotations", "-Wunused:nowarn", "-Ytasty-reader")
      else Nil
    val scala2Opts =
      if (sv.startsWith("2.")) Seq("-Xasync")
      else Nil
    // Enable experimental language features (needed for the `@unroll` annotation, SIP-61)
    val scala3Opts =
      if (sv.startsWith("3.")) Seq("-experimental")
      else Nil
    // Scala 3.8.x only supports Java 17+ output targets; bump the release there.
    val releaseVersion =
      if (sv.startsWith("3.") && jvmRelease.toInt < 17) "17"
      else jvmRelease
    super.scalacOptions() ++ scala212Opts ++ scala213Opts ++ scala2Opts ++ scala3Opts ++ Seq(
      "-deprecation",
      "-feature",
      "--release",
      releaseVersion
    )
  }
  def scalacPluginMvnDeps = Task {
    val sv = scalaVersion()
    val scala212Plugins =
      if (sv.startsWith("2.12.")) Seq(Deps.macroParadise)
      else Nil
    super.scalacPluginMvnDeps() ++ scala212Plugins
  }
}
