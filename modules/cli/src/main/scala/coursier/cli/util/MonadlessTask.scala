package coursier.cli.util

import coursier.util.Task

class MonadlessTask extends io.monadless.Monadless[Task] {

  def apply[T](v: => T): Task[T] =
    Task.delay(v)

  def collect[T](list: List[Task[T]]): Task[List[T]] =
    Task.gather.gather(list).map(_.toList)

  def rescue[T](m: Task[T])(pf: PartialFunction[Throwable, Task[T]]): Task[T] =
    m.attempt.flatMap {
      case Left(e) if pf.isDefinedAt(e) =>
        pf(e)
      case either =>
        Task.fromEither(either)
    }

  def ensure[T](m: Task[T])(f: => Unit): Task[T] =
    for {
      e <- m.attempt
      _ <- Task.delay(f).attempt
      t <- Task.fromEither(e)
    } yield t

}

object MonadlessTask extends MonadlessTask
