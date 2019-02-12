package coursier.util

import scala.concurrent.{ExecutionContext, Future, Promise}
import scala.util.{Failure, Success}

final case class Task[+T](value: ExecutionContext => Future[T]) extends AnyVal {

  def map[U](f: T => U): Task[U] =
    Task(implicit ec => value(ec).map(f))
  def flatMap[U](f: T => Task[U]): Task[U] =
    Task(implicit ec => value(ec).flatMap(t => f(t).value(ec)))

  def handle[U >: T](f: PartialFunction[Throwable, U]): Task[U] =
    Task(ec => value(ec).recover(f)(ec))

  def future()(implicit ec: ExecutionContext): Future[T] =
    value(ec)

  def attempt: Task[Either[Throwable, T]] =
    map(Right(_))
    .handle {
      case t: Throwable => Left(t)
    }
}

object Task extends PlatformTaskCompanion {

  def point[A](a: A): Task[A] = {
    val future = Future.successful(a)
    Task(_ => future)
  }

  def delay[A](a: => A): Task[A] =
    Task(ec => Future(a)(ec))

  def never[A]: Task[A] =
    Task(_ => Promise[A].future)

  def fromEither[T](e: Either[Throwable, T]): Task[T] =
    Task(_ => Future.fromTry(e.fold(Failure(_), Success(_))))

  def fail(e: Throwable): Task[Nothing] =
    Task(_ => Future.fromTry(Failure(e)))

  def tailRecM[A, B](a: A)(fn: A => Task[Either[A, B]]): Task[B] =
    Task[B] { implicit ec =>
      def loop(a: A): Future[B] =
        fn(a).future().flatMap {
          case Right(b) =>
            Future.successful(b)
          case Left(a) =>
            // this is safe because recursive
            // flatMap is safe on Future
            loop(a)
        }
      loop(a)
    }

  def gather: Gather[Task] =
    schedulable

}

