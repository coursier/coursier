package coursierbuild.modules

import coursierbuild.{Deps, ScalaVersions}
import mill.*
import mill.scalalib.*

/** Compiles the Scala 3 flavour of a module with [[ScalaVersions.scala3Tests]] rather than
  * [[ScalaVersions.scala3]], while its module dependencies stay on the latter.
  *
  * Only meant for test modules, and the test helpers they depend on: they're not published, so they
  * don't force their Scala 3 version onto anyone. Anything depending on such a module must mix this
  * in too, as earlier Scala 3 versions can't read the TASTy files of later ones.
  */
trait LatestScala3Module extends ScalaModule {
  override abstract def scalaVersion: T[String] = Task {
    val sv = super.scalaVersion()
    if (sv.startsWith("3.")) ScalaVersions.scala3Tests else sv
  }
  def scalacOptions = Task {
    val opts = super.scalacOptions()
    if (scalaVersion().startsWith("3."))
      // -Yfuture-lazy-vals (see CsScalaModule) is the default from Scala 3.8 on, and later
      // versions warn about it. The sources are written for ScalaVersions.scala3 (and are
      // often shared with Scala 2), so stick to its language version, rather than getting
      // migration warnings about `using` for implicit arguments, `x: _*`, etc.
      opts.filter(_ != "-Yfuture-lazy-vals") ++ Seq("-source", "3.3")
    else opts
  }
  def mvnDeps = Task {
    val sv = scalaVersion()
    // From Scala 3.8 on, scala3-library is empty, and the standard library is all in
    // scala-library. The module dependencies pull an earlier scala3-library, that has its own
    // copy of many scala-library classes. This evicts it (see `Cli#mvnDeps` too).
    val scala3Lib = if (sv.startsWith("3.")) Seq(Deps.scala3Library(sv)) else Nil
    super.mvnDeps() ++ scala3Lib
  }
}
