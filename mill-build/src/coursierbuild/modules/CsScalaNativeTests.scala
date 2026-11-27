package coursierbuild.modules

import mill.scalanativelib.*

/** Mixed in the Scala Native test modules, after Mill's `ScalaNativeTests`.
  *
  * Used as a marker to select the Scala Native test modules with `__:CsScalaNativeTests`.
  */
trait CsScalaNativeTests extends TestScalaNativeModule
