package coursierbuild.modules

import mill.scalajslib.*

/** Mixed in the Scala.js test modules, after Mill's `ScalaJSTests`.
  *
  * Used as a marker by the CI, which selects the Scala.js test modules with `__:CsScalaJsTests`.
  */
trait CsScalaJsTests extends TestScalaJSModule
