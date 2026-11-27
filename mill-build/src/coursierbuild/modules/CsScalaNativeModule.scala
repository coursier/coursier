package coursierbuild.modules

import coursierbuild.ScalaVersions
import mill._, mill.scalalib._, mill.scalanativelib._

/** The Scala Native modules are all built with Scala 3 only, and aren't cross-built, like the
  * Scala.js ones (see [[CsScalaJsModule]]).
  */
trait CsScalaNativeModule extends ScalaNativeModule with CsScala3Module {
  def scalaNativeVersion  = ScalaVersions.scalaNative
  def scalacPluginMvnDeps = Task(super[ScalaNativeModule].scalacPluginMvnDeps())
  // --release only makes sense when targetting the JVM, and Scala Native's javalib
  // isn't a strict subset of any JDK release anyway. -Yfuture-lazy-vals (see CsScalaModule)
  // picks the lazy vals implementation of JVM bytecode, and requires --release.
  def scalacOptions = Task {
    val opts = super.scalacOptions().filter(_ != "-Yfuture-lazy-vals")
    val idx  = opts.indexOf("--release")
    if (idx < 0) opts
    else opts.take(idx) ++ opts.drop(idx + 2)
  }
  // From Scala 3.8 on, scalalib_native0.5_3 holds the whole standard library, and replaces
  // scalalib_native0.5_2.13. But Scala 2.13 artifacts used from Scala 3 (portable-scala-reflect,
  // via scala-java-time) still pull the latter, that has its own copy of the same classes, and that
  // nothing evicts, as its module name differs. Map it to the former, the way scala-library 2.13
  // gets evicted by scala-library 3.8+ on the JVM.
  def mapDependencies = Task.Anon {
    val sv     = scalaVersion()
    val f      = super.mapDependencies()
    val prefix = s"scalalib_native${scalaNativeBinaryVersion()}_"
    val scala2 = coursier.ModuleName(prefix + "2.13")
    val scala3 = coursier.ModuleName(prefix + "3")
    val version =
      coursier.version.VersionConstraint(s"$sv+${scalaNativeVersion()}")
    if (sv.startsWith("3.") && CsScalaModule.scala3Minor(sv) >= 8) { (dep: coursier.Dependency) =>
      val dep0 = f(dep)
      if (dep0.module.organization.value == "org.scala-native" && dep0.module.name == scala2)
        dep0
          .withModule(dep0.module.withName(scala3))
          .withVersionConstraint(version)
      else dep0
    }
    else f
  }
}
