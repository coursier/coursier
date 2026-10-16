package coursierbuild.modules

import mill.javalib.TestModule

// The `test` jobs of the CI pick the test modules they run with type selectors on these traits,
// rather than via a command aggregating the test tasks: under Mill's selective execution, such a
// command would re-run every test as soon as a single one is affected by a change, see
// .github/scripts/selective-tests.sh. There's one `test` job per Scala version and OS.

/** A test module the `test` jobs of the CI run, in the jobs of the default Scala version (2.13)
  *
  * For modules that aren't cross-built, see [[CrossJvmCiTests]] for the cross-built ones.
  */
trait JvmCiTests extends TestModule

/** A test module of a cross-built module, that the `test` jobs of the CI run in the jobs of its
  * Scala version
  *
  * Selected with `__[2.13.18].__:CrossJvmCiTests` and the like.
  */
trait CrossJvmCiTests extends TestModule
