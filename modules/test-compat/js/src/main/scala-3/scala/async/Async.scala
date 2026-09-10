package scala.async

/** Scala.js stand-in for `io.github.dotty-cps-async::shim-scala-async`, which upstream only
  * publishes for the JVM.
  *
  * scala-async is a Scala 2-only macro library. dotty-cps-async provides a SIP-22 compatible
  * interface with the same `async[T](body)(using ExecutionContext): Future[T]` /
  * `await[T](Future[T]): T` signatures, and aliasing its object here keeps the test sources'
  * `import scala.async.Async.{async, await}` working unchanged on Scala 3.
  */
val Async = cps.compat.FutureAsync
