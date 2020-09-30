package coursier.util

import java.util.concurrent.ScheduledExecutorService

import scala.concurrent.duration.Duration
import scala.concurrent.{ExecutionContext, Future, Promise}
import scala.util.{Failure, Success}

final case class Task[+T](value: ExecutionContext => Future[T]) extends AnyVal {

  def map[U](f: T => U): Task[U] =
    Task(implicit ec => value(ec).map(f))
  def flatMap[U](f: T => Task[U]): Task[U] =
    Task(implicit ec => value(ec).flatMap(t => Task.wrap(f(t)).value(ec)))

  def handle[U >: T](f: PartialFunction[Throwable, U]): Task[U] =
    Task(ec => value(ec).recover(f)(ec))

  def future()(implicit ec: ExecutionContext): Future[T] =
    value(ec)

  def attempt: Task[Either[Throwable, T]] =
    map(Right(_))
    .handle {
      case t: Throwable => Left(t)
    }

  def schedule(duration: Duration, es: ScheduledExecutorService): Task[T] = {
    Task { implicit ec =>
      val p = Promise[T]()
      val r: Runnable =
        new Runnable {
          def run() = value(ec).onComplete(p.complete)
        }
      es.schedule(r, duration.length, duration.unit)
      p.future
    }
  }
}

object Task extends PlatformTaskCompanion {

  def point[A](a: A): Task[A] = {
    val future = Future.successful(a)
    Task(_ => future)
  }

  def delay[A](a: => A): Task[A] =
    Task(ec => Future(wrap(a))(ec))

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
    sync

  // When a thunk throws throwables like IllegalAccessError from Future(...),
  // Future does NOT catch it, and never completes the Future.
  // Wrapping such exception in this allows to circumvent that, and have the Future
  // complete anyway.
  final class WrappedException(cause: Throwable) extends Throwable(cause)
  @inline
  private def wrap[T](t: => T): T =
    try t
    catch {
      // other exceptions can be added here if needed
      case e: LinkageError =>
        throw new WrappedException(e)
    }

}

