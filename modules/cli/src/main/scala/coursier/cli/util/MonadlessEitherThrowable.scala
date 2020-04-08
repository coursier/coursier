package coursier.cli.util

import io.monadless.Monadless

import scala.util.control.NonFatal

class MonadlessEitherThrowable extends Monadless[({ type L[T] = Either[Throwable, T] })#L] {

  def apply[T](v: => T): Either[Throwable, T] =
    Right(v)

  def collect[T](list: List[Either[Throwable, T]]): Either[Throwable, List[T]] =
    list
      .foldLeft[Either[Throwable, List[T]]](Right(Nil)) {
        (acc, elem) =>
          for {
            acc0 <- acc
            t <- elem
          } yield t :: acc0
      }
      .map(_.reverse)

  def rescue[T](m: Either[Throwable, T])(pf: PartialFunction[Throwable, Either[Throwable, T]]): Either[Throwable, T] =
    m.left.flatMap { e =>
      if (pf.isDefinedAt(e))
        pf(e)
      else
        Left(e)
    }

  def ensure[T](m: Either[Throwable, T])(f: => Unit): Either[Throwable, T] = {
    try f
    catch {
      case NonFatal(_) => ()
    }
    m
  }

}

object MonadlessEitherThrowable extends MonadlessEitherThrowable
