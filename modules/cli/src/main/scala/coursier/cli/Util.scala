package coursier.cli

import java.io.PrintStream

import cats.data.ValidatedNel

object Util {

  def prematureExit(msg: String): Nothing = {
    Console.err.println(msg)
    sys.exit(255)
  }

  def prematureExitIf(cond: Boolean)(msg: => String): Unit =
    if (cond)
      prematureExit(msg)

  def exit(msg: String): Nothing = {
    Console.err.println(msg)
    sys.exit(1)
  }

  def exitIf(cond: Boolean)(msg: => String): Unit =
    if (cond)
      exit(msg)

  implicit class ValidatedExitOnError[T](private val validated: ValidatedNel[String, T]) extends AnyVal {
    def exitOnError(errStream: PrintStream = System.err, exitCode: Int = 1): T =
      validated.toEither match {
        case Left(errors) =>
          for (err <- errors.toList)
            errStream.println(err)
          sys.exit(exitCode)
        case Right(t) => t
      }
  }

}
