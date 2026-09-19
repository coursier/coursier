package coursierbuild.modules

import com.goyeau.mill.scalafix.ScalafixModule
import coursierbuild.Deps
import mill._, mill.scalalib._

trait CsScalaModule extends ScalaModule with CoursierJavaModule with ScalafixModule {
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
    // Use the JDK 25+ compatible lazy vals implementation (the default from Scala 3.8 on).
    // It requires an explicit output version, which the `--release` option below provides.
    val scala3Opts =
      if (sv.startsWith("3.")) Seq("-Yfuture-lazy-vals")
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
  // rules from modules/scalafix-rules, enabled by name in .scalafix.conf
  // (build_.package_ is the root module of build.mill, which is compiled along with this file)
  def scalafixToolClasspath = Task {
    super.scalafixToolClasspath() ++ build_.package_.`scalafix-rules`.localClasspath()
  }
  def scalacPluginMvnDeps = Task {
    val sv = scalaVersion()
    val scala212Plugins =
      if (sv.startsWith("2.12.")) Seq(Deps.macroParadise)
      else Nil
    super.scalacPluginMvnDeps() ++ scala212Plugins
  }
}
