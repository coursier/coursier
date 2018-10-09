package coursier.test.util

import coursier.util.Task

import scala.concurrent.{ExecutionContext, Future}

trait ToFuture[F[_]] {
  def toFuture[T](ec: ExecutionContext, f: F[T]): Future[T]
}

object ToFuture {

  def apply[F[_]](implicit toFuture: ToFuture[F]): ToFuture[F] =
    toFuture

  implicit val taskToFuture: ToFuture[Task] =
    new ToFuture[Task] {
      def toFuture[T](ec: ExecutionContext, f: Task[T]) =
        f.future()(ec)
    }

}
