package coursier.tests

import utest.asserts.{AssertEntry, Asserts, Tracer}

import scala.quoted.*

/** utest's `assert` for test sources that assert inside an `async` block.
  *
  * `utest.assert` takes varargs, and dotty-cps-async (which backs `async` on Scala 3) cannot
  * transform the bare `SeqLiteral` that produces, failing with "language construction is not
  * supported". This is the very same assertion, tracer and reporting included, only handed its
  * single entry directly rather than through varargs.
  *
  * The Scala 2 counterpart is just utest's own `assert` (scala-async has no such limitation).
  */
object AssertCompat {

  inline def assert(inline expr: Boolean): Unit = ${ assertImpl('expr) }

  /** A `val`, not a lambda literal: `Tracer.traceOne` beta-reduces the application it builds, and
    * reducing a lambda there drops the bindings of the `Inlined` trees dotty-cps-async generates
    * (the backend then fails with "key not found: val ec$proxy..."). `Expr.betaReduce` leaves an
    * application of a stable reference alone.
    */
  val assertOne: AssertEntry[Boolean] => Unit =
    entry => Asserts.assertImpl(entry)

  private def assertImpl(expr: Expr[Boolean])(using Quotes): Expr[Unit] =
    Tracer.traceOne[Boolean, Unit]('{ AssertCompat.assertOne }, expr)
}
