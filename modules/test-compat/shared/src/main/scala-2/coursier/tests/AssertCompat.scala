package coursier.tests

/** utest's `assert`, under the name the shared test sources import.
  *
  * scala-async copes with utest's vararg `assert` just fine, so this is utest's own macro. See the
  * Scala 3 counterpart under `src/main/scala-3` for why it needs an alias at all.
  */
object AssertCompat extends utest.asserts.Asserts
