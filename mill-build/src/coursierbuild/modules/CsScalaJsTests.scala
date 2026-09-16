package coursierbuild.modules

import mill.*
import mill.api.PathRef
import mill.scalajslib.*

/** Mixed in the Scala.js test modules, after Mill's `ScalaJSTests`.
  *
  * Mill resolves `scalaJSTestDeps` (the Scala.js test bridge and its dependencies) for Scala 2.13,
  * without reconciling it with the Scala version of the module. On Scala 3, that hands the linker
  * the Scala 2.13 `scalajs-scalalib` alongside the Scala 3 one that the module itself depends on
  * (via `scala3-library_sjs1`), and the Scala 2.13 IR wins - so anything the Scala 3 standard
  * library added on top of the Scala 2.13 one, like the `Option#orNull` overload of
  * https://github.com/scala/scala3/pull/25733, fails to link with "Referring to non-existent
  * method". Drop it from those dependencies, the module brings the right one in.
  */
trait CsScalaJsTests extends TestScalaJSModule {
  def scalaJSTestDeps = Task {
    val deps = super.scalaJSTestDeps()
    if (scalaVersion().startsWith("3."))
      deps.filter(ref => !ref.path.last.startsWith("scalajs-scalalib_"))
    else
      deps
  }
}
